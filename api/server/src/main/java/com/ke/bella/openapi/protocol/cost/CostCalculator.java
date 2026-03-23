package com.ke.bella.openapi.protocol.cost;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.ke.bella.openapi.protocol.asr.diarization.SpeakerDiarizationLogHandler;
import com.ke.bella.openapi.protocol.asr.diarization.SpeakerDiarizationPriceInfo;
import com.ke.bella.openapi.protocol.asr.flash.FlashAsrPriceInfo;
import com.ke.bella.openapi.protocol.asr.transcription.TranscriptionsAsrPriceInfo;
import com.ke.bella.openapi.protocol.completion.CompletionPriceInfo;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesApiResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesPriceInfo;
import com.ke.bella.openapi.protocol.embedding.EmbeddingPriceInfo;
import com.ke.bella.openapi.protocol.embedding.EmbeddingResponse;
import com.ke.bella.openapi.protocol.images.ImagesEditsPriceInfo;
import com.ke.bella.openapi.protocol.images.ImagesPriceInfo;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.protocol.ocr.OcrPriceInfo;
import com.ke.bella.openapi.protocol.realtime.RealTimePriceInfo;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingLogHandler;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingPriceInfo;
import com.ke.bella.openapi.protocol.tts.TtsPriceInfo;
import com.ke.bella.openapi.protocol.video.VideoPriceInfo;
import com.ke.bella.openapi.protocol.video.VideoUsage;
import com.ke.bella.openapi.protocol.web.WebCrawlPriceInfo;
import com.ke.bella.openapi.protocol.web.WebCrawlUsage;
import com.ke.bella.openapi.protocol.web.WebExtractPriceInfo;
import com.ke.bella.openapi.protocol.web.WebExtractUsage;
import com.ke.bella.openapi.protocol.web.WebSearchPriceInfo;
import com.ke.bella.openapi.protocol.web.WebSearchUsage;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.MatchUtils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CostCalculator {

    public static CostDetails calculate(String endpoint, String priceInfo, Object usage) {
        EndpointCostCalculator calculator = Arrays.stream(CostCalculators.values()).filter(t -> MatchUtils.matchUrl(t.endpoint, endpoint))
                .findAny()
                .map(CostCalculators::getCalculator)
                .orElseThrow(() -> new RuntimeException("no calculator implemented for " + endpoint));
        return calculator.calculate(priceInfo, usage);
    }

    public static boolean validate(String endpoint, String priceInfo) {
        EndpointCostCalculator calculator = Arrays.stream(CostCalculators.values()).filter(t -> MatchUtils.matchUrl(t.endpoint, endpoint))
                .findAny()
                .map(CostCalculators::getCalculator)
                .orElse(null);
        if(calculator == null) {
            log.warn("no calculator implemented for " + endpoint);
            return true;
        }
        return calculator.checkPriceInfo(priceInfo);
    }

    @AllArgsConstructor
    @Getter
    enum CostCalculators {
        COMPLETION("/v*/chat/completions", completion),
        MESSAGES("/v*/messages", completion),
        RESPONSES("/v*/responses", responses),
        GEMINI("/v1beta/models", completion),
        EMBEDDING("/v*/embeddings", embedding),
        TTS("/v*/audio/speech", tts),
        ASR_FLASH("/v*/audio/asr/flash", asr_flash),
        ASR_STREAM("/v*/audio/asr/stream", realtime),
        REAL_TIME("/v*/audio/realtime", realtime),
        ASR_TRANSCRIPTIONS("/v*/audio/transcriptions", asr_transcriptions),
        SPEAKER_EMBEDDING("/v*/audio/speaker/embedding", speakerEmbedding),
        SPEAKER_DIARIZATION("/v*/audio/speaker/diarization", speakerDiarization),
        IMAGES("/v*/images/generations", images),
        IMAGES_EDITS("/v*/images/edits", images_edits),
        IMAGES_VARIATIONS("/v*/images/variations", images),
        WEB_SEARCH("/v*/web/search", webSearch),
        WEB_CRAWL("/v*/web/crawl", webCrawl),
        WEB_EXTRACT("/v*/web/extract", webExtract),
        OCR("/v*/ocr/*", ocr),
        VIDEOS("/v*/videos", video),
        ;

        final String endpoint;
        final EndpointCostCalculator calculator;
    }

    static EndpointCostCalculator completion = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            CompletionPriceInfo price = JacksonUtils.deserialize(priceInfo, CompletionPriceInfo.class);
            CompletionResponse.TokenUsage tokenUsage;
            if(usage instanceof CompletionResponse.TokenUsage) {
                tokenUsage = (CompletionResponse.TokenUsage) usage;
            } else {
                String usageJson = JacksonUtils.serialize(usage);
                tokenUsage = JacksonUtils.deserialize(usageJson, CompletionResponse.TokenUsage.class);
            }
            int promptTokens = tokenUsage.getPrompt_tokens();
            int completionsTokens = tokenUsage.getCompletion_tokens();
            CompletionPriceInfo.RangePrice rangePrice = price.matchRangePrice(promptTokens, completionsTokens);

            Map<String, CostDetails.CostDetailItem> inputDetails = new HashMap<>();
            Map<String, CostDetails.CostDetailItem> outputDetails = new HashMap<>();
            Pair<BigDecimal, Integer> inputParts = CompletionsCalHelper.calculateAllElements(CompletionsCalHelper.INPUT, rangePrice,
                    tokenUsage.getPrompt_tokens_details(), inputDetails);
            Pair<BigDecimal, Integer> outputParts = CompletionsCalHelper.calculateAllElements(CompletionsCalHelper.OUTPUT, rangePrice,
                    tokenUsage.getCompletion_tokens_details(), outputDetails);
            if(inputParts.getLeft().doubleValue() > 0) {
                promptTokens -= inputParts.getRight();
            }
            if(outputParts.getLeft().doubleValue() > 0) {
                completionsTokens -= outputParts.getRight();
            }

            BigDecimal totalCost = inputParts.getLeft().add(outputParts.getLeft());
            if(promptTokens > 0) {
                BigDecimal promptCost = rangePrice.getInput().multiply(BigDecimal.valueOf(promptTokens / 1000.0));
                totalCost = totalCost.add(promptCost);
                inputDetails.put("prompt_tokens", CostDetails.CostDetailItem.builder()
                        .tokens(promptTokens).unitPrice(rangePrice.getInput())
                        .cost(promptCost).build());
            }
            if(completionsTokens > 0) {
                BigDecimal completionCost = rangePrice.getOutput().multiply(BigDecimal.valueOf(completionsTokens / 1000.0));
                totalCost = totalCost.add(completionCost);
                outputDetails.put("completion_tokens", CostDetails.CostDetailItem.builder()
                        .tokens(completionsTokens).unitPrice(rangePrice.getOutput())
                        .cost(completionCost).build());
            }

            return CostDetails.builder()
                    .totalCost(totalCost)
                    .inputDetails(inputDetails.isEmpty() ? null : inputDetails)
                    .outputDetails(outputDetails.isEmpty() ? null : outputDetails)
                    .build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            CompletionPriceInfo price = JacksonUtils.deserialize(priceInfo, CompletionPriceInfo.class);
            if(price == null) {
                return false;
            }
            return price.validate();
        }
    };

    static EndpointCostCalculator embedding = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            EmbeddingPriceInfo price = JacksonUtils.deserialize(priceInfo, EmbeddingPriceInfo.class);
            EmbeddingResponse.TokenUsage tokenUsage = (EmbeddingResponse.TokenUsage) usage;
            return CostDetails.builder().totalCost(price.getInput().multiply(BigDecimal.valueOf(tokenUsage.getPrompt_tokens() / 1000.0))).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            EmbeddingPriceInfo price = JacksonUtils.deserialize(priceInfo, EmbeddingPriceInfo.class);
            return price != null && price.getInput() != null;
        }
    };

    static EndpointCostCalculator tts = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            TtsPriceInfo price = JacksonUtils.deserialize(priceInfo, TtsPriceInfo.class);
            int inputLength = (int) usage;
            return CostDetails.builder().totalCost(price.getInput().multiply(BigDecimal.valueOf(inputLength / 10000.0))).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            TtsPriceInfo price = JacksonUtils.deserialize(priceInfo, TtsPriceInfo.class);
            return price != null && price.getInput() != null;
        }
    };

    static EndpointCostCalculator asr_flash = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            FlashAsrPriceInfo price = JacksonUtils.deserialize(priceInfo, FlashAsrPriceInfo.class);
            return CostDetails.builder().totalCost(price.getPrice()).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            FlashAsrPriceInfo price = JacksonUtils.deserialize(priceInfo, FlashAsrPriceInfo.class);
            return price != null && price.getPrice() != null;
        }
    };

    static EndpointCostCalculator realtime = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            RealTimePriceInfo price = JacksonUtils.deserialize(priceInfo, RealTimePriceInfo.class);
            return CostDetails.builder()
                    .totalCost(BigDecimal.valueOf((price.getPrice().doubleValue() / 3600 * 100) * Double.valueOf(usage.toString()))).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            RealTimePriceInfo price = JacksonUtils.deserialize(priceInfo, RealTimePriceInfo.class);
            return price != null && price.getPrice() != null;
        }
    };

    static EndpointCostCalculator asr_transcriptions = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            TranscriptionsAsrPriceInfo price = JacksonUtils.deserialize(priceInfo, TranscriptionsAsrPriceInfo.class);
            return CostDetails.builder().totalCost(BigDecimal.valueOf((price.getPrice().doubleValue() / 3600 * 100) * Double.valueOf(usage.toString()))).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            TranscriptionsAsrPriceInfo price = JacksonUtils.deserialize(priceInfo, TranscriptionsAsrPriceInfo.class);
            return price != null && price.getPrice() != null;
        }
    };

    static EndpointCostCalculator images = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            ImagesPriceInfo price = JacksonUtils.deserialize(priceInfo, ImagesPriceInfo.class);
            ImagesResponse.Usage imageUsage = (ImagesResponse.Usage) usage;
            BigDecimal imageCost = BigDecimal.ZERO;
            // 获取质量和尺寸信息
            String quality = imageUsage.getQuality();
            String size = imageUsage.getSize();
            int imageCount = imageUsage.getNum();
            if(price != null && CollectionUtils.isNotEmpty(price.getDetails())) {
                ImagesPriceInfo.ImagesPriceInfoDetails details = price.getDetails().stream().filter(d -> d.getSize().equals(size)).findAny()
                        .orElse(price.getDetails().get(0));

                if(details.getImageTokenPrice() != null || details.getTextTokenPrice() != null) {
                    if(imageUsage.getInput_tokens_details() != null) {
                        BigDecimal textTokenCost = Optional.ofNullable(details.getTextTokenPrice()).orElse(BigDecimal.ZERO)
                                .multiply(BigDecimal.valueOf(Optional.of(imageUsage.getInput_tokens_details().getText_tokens()).orElse(0) / 1000.0));
                        BigDecimal imageTokenCost = Optional.ofNullable(details.getImageTokenPrice()).orElse(BigDecimal.ZERO)
                                .multiply(BigDecimal.valueOf(Optional.of(imageUsage.getInput_tokens_details().getImage_tokens()).orElse(0) / 1000.0));
                        imageCost = imageCost.add(textTokenCost).add(imageTokenCost);
                    }
                }
                if(quality.equals("low")) {
                    imageCost = imageCost.add(details.getLdPricePerImage().multiply(BigDecimal.valueOf(imageCount)));
                } else if(quality.equals("medium")) {
                    imageCost = imageCost.add(details.getMdPricePerImage().multiply(BigDecimal.valueOf(imageCount)));
                } else {
                    imageCost = imageCost.add(details.getHdPricePerImage().multiply(BigDecimal.valueOf(imageCount)));
                }
            }
            return CostDetails.builder().totalCost(imageCost).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            ImagesPriceInfo price = JacksonUtils.deserialize(priceInfo, ImagesPriceInfo.class);

            return price != null && CollectionUtils.isNotEmpty(price.getDetails())
                    && price.getDetails().stream().allMatch(d -> StringUtils.isNotBlank(d.getSize()) &&
                            d.getHdPricePerImage() != null && d.getMdPricePerImage() != null && d.getLdPricePerImage() != null);
        }
    };

    static EndpointCostCalculator images_edits = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            ImagesEditsPriceInfo price = JacksonUtils.deserialize(priceInfo, ImagesEditsPriceInfo.class);
            ImagesResponse.Usage imageEditsUsage = (ImagesResponse.Usage) usage;
            BigDecimal editCost = BigDecimal.ZERO;

            if(price != null && (price.getPricePerEdit() != null || price.getImageTokenPrice() != null)) {

                int editCount = imageEditsUsage.getNum();
                if(price.getPricePerEdit() != null) {
                    editCost = editCost.add(price.getPricePerEdit().multiply(BigDecimal.valueOf(editCount)));
                }

                if(price.getImageTokenPrice() != null && imageEditsUsage.getTotal_tokens() != null) {
                    BigDecimal imageTokenCost = price.getImageTokenPrice()
                            .multiply(BigDecimal.valueOf(imageEditsUsage.getTotal_tokens() / 1000.0));
                    editCost = editCost.add(imageTokenCost);
                }
            } else if(imageEditsUsage.getTotal_tokens() != null) {
                // 临时补丁：priceInfo 格式不兼容时，尝试直接读取 imageOutput 字段兜底计算
                com.fasterxml.jackson.databind.JsonNode node = JacksonUtils.deserialize(priceInfo);
                if(node != null) {
                    com.fasterxml.jackson.databind.JsonNode imageOutputNode = node.path("tiers").path(0).path("inputRangePrice").path("imageOutput");
                    if(!imageOutputNode.isMissingNode() && !imageOutputNode.isNull()) {
                        log.warn("images_edits price format mismatch, fallback to imageOutput calculation, priceInfo={}", priceInfo);
                        BigDecimal imageOutput = imageOutputNode.decimalValue();
                        editCost = imageOutput.multiply(BigDecimal.valueOf(imageEditsUsage.getTotal_tokens() / 1000.0));
                    }
                }
            }

            return CostDetails.builder().totalCost(editCost).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {

            ImagesEditsPriceInfo price = JacksonUtils.deserialize(priceInfo, ImagesEditsPriceInfo.class);
            return price != null && price.getPricePerEdit() != null;
        }
    };

    static EndpointCostCalculator speakerEmbedding = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            SpeakerEmbeddingPriceInfo priceConfig = JacksonUtils.deserialize(priceInfo, SpeakerEmbeddingPriceInfo.class);
            if(priceConfig.getPrice() == null) {
                return CostDetails.builder().totalCost(BigDecimal.ZERO).build();
            }

            // usage可能是Double类型(duration秒数)或者SpeakerEmbeddingUsage类型
            double durationSeconds = 0.0;
            if(usage instanceof Double) {
                durationSeconds = (Double) usage;
            } else if(usage instanceof SpeakerEmbeddingLogHandler.SpeakerEmbeddingUsage) {
                SpeakerEmbeddingLogHandler.SpeakerEmbeddingUsage speakerUsage = (SpeakerEmbeddingLogHandler.SpeakerEmbeddingUsage) usage;
                durationSeconds = speakerUsage.getDurationSeconds();
            } else if(usage != null) {
                // 尝试解析为数字
                try {
                    durationSeconds = Double.valueOf(usage.toString());
                } catch (NumberFormatException e) {
                    return CostDetails.builder().totalCost(BigDecimal.ZERO).build();
                }
            }

            // 基于时长计算成本：price（元/小时） × duration（小时）
            // 将秒转换为小时：durationSeconds / 3600
            double durationHours = durationSeconds / 3600.0;
            return CostDetails.builder().totalCost(priceConfig.getPrice().multiply(BigDecimal.valueOf(durationHours))).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            SpeakerEmbeddingPriceInfo priceConfig = JacksonUtils.deserialize(priceInfo, SpeakerEmbeddingPriceInfo.class);
            return priceConfig != null && priceConfig.getPrice() != null &&
                    priceConfig.getPrice().compareTo(BigDecimal.ZERO) >= 0;
        }
    };

    static EndpointCostCalculator speakerDiarization = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            SpeakerDiarizationPriceInfo priceConfig = JacksonUtils.deserialize(priceInfo, SpeakerDiarizationPriceInfo.class);
            if(priceConfig.getPrice() == null) {
                return CostDetails.builder().totalCost(BigDecimal.ZERO).build();
            }

            // usage 就是 SpeakerDiarizationUsage 类型
            if(usage instanceof SpeakerDiarizationLogHandler.SpeakerDiarizationUsage) {
                SpeakerDiarizationLogHandler.SpeakerDiarizationUsage diarizationUsage = (SpeakerDiarizationLogHandler.SpeakerDiarizationUsage) usage;
                int audioDurationSeconds = diarizationUsage.getAudioDurationSeconds();

                // 基于音频时长计算成本：price（元/小时） × audioDurationHours（小时）
                // 将秒转换为小时：audioDurationSeconds / 3600.0
                double audioDurationHours = audioDurationSeconds / 3600.0;
                return CostDetails.builder().totalCost(priceConfig.getPrice().multiply(BigDecimal.valueOf(audioDurationHours))).build();
            }
            return CostDetails.builder().totalCost(BigDecimal.ZERO).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            SpeakerDiarizationPriceInfo priceConfig = JacksonUtils.deserialize(priceInfo, SpeakerDiarizationPriceInfo.class);
            return priceConfig != null && priceConfig.getPrice() != null &&
                    priceConfig.getPrice().compareTo(BigDecimal.ZERO) >= 0;
        }
    };

    static EndpointCostCalculator webSearch = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            WebSearchPriceInfo price = JacksonUtils.deserialize(priceInfo, WebSearchPriceInfo.class);
            WebSearchUsage searchUsage = (WebSearchUsage) usage;

            // Determine search depth from usage and calculate cost
            // Basic Search: 1 API credit per request
            // Advanced Search: 2 API credits per request
            String searchDepth = searchUsage.getSearchDepth();
            BigDecimal cost = "advanced".equalsIgnoreCase(searchDepth) ? price.getAdvancedSearchPrice() : price.getBasicSearchPrice();
            return CostDetails.builder().totalCost(cost).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            WebSearchPriceInfo price = JacksonUtils.deserialize(priceInfo, WebSearchPriceInfo.class);
            return price != null && price.getBasicSearchPrice() != null && price.getAdvancedSearchPrice() != null;
        }
    };

    static EndpointCostCalculator webCrawl = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            WebCrawlPriceInfo price = JacksonUtils.deserialize(priceInfo, WebCrawlPriceInfo.class);
            WebCrawlUsage crawlUsage = (WebCrawlUsage) usage;

            BigDecimal totalCost = BigDecimal.ZERO;

            // Calculate mapping cost based on pages mapped
            int pagesMapped = crawlUsage.getPagesMapped();
            boolean hasInstructions = crawlUsage.isHasInstructions();

            if(hasInstructions) {
                totalCost = totalCost.add(price.getInstructionMappingPrice().multiply(BigDecimal.valueOf(pagesMapped)));
            } else {
                totalCost = totalCost.add(price.getBasicMappingPrice().multiply(BigDecimal.valueOf(pagesMapped)));
            }

            // Calculate extraction cost based on successful extractions
            int successfulExtractions = crawlUsage.getSuccessfulExtractions();
            String extractDepth = crawlUsage.getExtractDepth();

            if("advanced".equalsIgnoreCase(extractDepth)) {
                totalCost = totalCost.add(price.getAdvancedExtractionPrice().multiply(BigDecimal.valueOf(successfulExtractions)));
            } else {
                totalCost = totalCost.add(price.getBasicExtractionPrice().multiply(BigDecimal.valueOf(successfulExtractions)));
            }

            return CostDetails.builder().totalCost(totalCost).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            WebCrawlPriceInfo price = JacksonUtils.deserialize(priceInfo, WebCrawlPriceInfo.class);
            return price != null &&
                    price.getBasicMappingPrice() != null &&
                    price.getInstructionMappingPrice() != null &&
                    price.getBasicExtractionPrice() != null &&
                    price.getAdvancedExtractionPrice() != null;
        }
    };

    static EndpointCostCalculator webExtract = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            WebExtractPriceInfo price = JacksonUtils.deserialize(priceInfo, WebExtractPriceInfo.class);
            WebExtractUsage extractUsage = (WebExtractUsage) usage;

            BigDecimal totalCost = BigDecimal.ZERO;

            // Calculate extraction cost based on successful extractions
            int successfulExtractions = extractUsage.getSuccessfulExtractions();
            String extractDepth = extractUsage.getExtractDepth();

            if("advanced".equalsIgnoreCase(extractDepth)) {
                totalCost = totalCost.add(price.getAdvancedExtractionPrice().multiply(BigDecimal.valueOf(successfulExtractions)));
            } else {
                totalCost = totalCost.add(price.getBasicExtractionPrice().multiply(BigDecimal.valueOf(successfulExtractions)));
            }

            return CostDetails.builder().totalCost(totalCost).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            WebExtractPriceInfo price = JacksonUtils.deserialize(priceInfo, WebExtractPriceInfo.class);
            return price != null &&
                    price.getBasicExtractionPrice() != null &&
                    price.getAdvancedExtractionPrice() != null;
        }
    };
    static EndpointCostCalculator ocr = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            OcrPriceInfo price = JacksonUtils.deserialize(priceInfo, OcrPriceInfo.class);
            int times = Integer.parseInt(usage.toString());
            return CostDetails.builder().totalCost(price.getPricePerRequest().multiply(BigDecimal.valueOf(times))).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            OcrPriceInfo price = JacksonUtils.deserialize(priceInfo, OcrPriceInfo.class);
            return price != null && price.getPricePerRequest() != null;
        }
    };

    static EndpointCostCalculator video = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            VideoPriceInfo price = JacksonUtils.deserialize(priceInfo, VideoPriceInfo.class);
            VideoUsage videoUsage;
            if(usage instanceof VideoUsage) {
                videoUsage = (VideoUsage) usage;
            } else {
                String usageJson = JacksonUtils.serialize(usage);
                videoUsage = JacksonUtils.deserialize(usageJson, VideoUsage.class);
            }

            int completionTokens = videoUsage.getCompletion_tokens() != null ? videoUsage.getCompletion_tokens() : 0;
            return CostDetails.builder().totalCost(price.getOutput().multiply(BigDecimal.valueOf(completionTokens / 1000.0))).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            VideoPriceInfo price = JacksonUtils.deserialize(priceInfo, VideoPriceInfo.class);
            return price != null && price.getOutput() != null;
        }
    };

    static EndpointCostCalculator responses = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            ResponsesPriceInfo price = JacksonUtils.deserialize(priceInfo, ResponsesPriceInfo.class);

            ResponsesApiResponse.Usage responsesUsage = null;
            CompletionResponse.TokenUsage tokenUsage = null;

            if(usage instanceof ResponsesApiResponse.Usage) {
                responsesUsage = (ResponsesApiResponse.Usage) usage;
            } else if(usage instanceof CompletionResponse.TokenUsage) {
                tokenUsage = (CompletionResponse.TokenUsage) usage;
            } else {
                String usageJson = JacksonUtils.serialize(usage);
                responsesUsage = JacksonUtils.deserialize(usageJson, ResponsesApiResponse.Usage.class);
            }

            Map<String, CostDetails.CostDetailItem> inputDetails = new HashMap<>();
            Map<String, CostDetails.CostDetailItem> outputDetails = new HashMap<>();
            Map<String, CostDetails.ToolCostDetailItem> toolDetails = new HashMap<>();
            BigDecimal totalCost = BigDecimal.ZERO;

            int inputTokens = 0;
            int outputTokens = 0;
            Integer cachedTokens = null;
            Integer reasoningTokens = null;
            Map<String, Integer> toolUsage = null;

            if(responsesUsage != null) {
                inputTokens = responsesUsage.getInput_tokens() != null ? responsesUsage.getInput_tokens() : 0;
                outputTokens = responsesUsage.getOutput_tokens() != null ? responsesUsage.getOutput_tokens() : 0;
                if(responsesUsage.getInput_tokens_details() != null) {
                    cachedTokens = responsesUsage.getInput_tokens_details().getCached_tokens();
                }
                if(responsesUsage.getOutput_tokens_details() != null) {
                    reasoningTokens = responsesUsage.getOutput_tokens_details().getReasoning_tokens();
                }
                toolUsage = responsesUsage.getTool_usage();
            } else if(tokenUsage != null) {
                inputTokens = tokenUsage.getPrompt_tokens();
                outputTokens = tokenUsage.getCompletion_tokens();
                if(tokenUsage.getPrompt_tokens_details() != null) {
                    cachedTokens = tokenUsage.getPrompt_tokens_details().getCached_tokens();
                }
                if(tokenUsage.getCompletion_tokens_details() != null) {
                    reasoningTokens = tokenUsage.getCompletion_tokens_details().getReasoning_tokens();
                }
            }

            ResponsesPriceInfo.RangePrice rangePrice = price.matchRangePrice(inputTokens, outputTokens);
            if(cachedTokens != null && cachedTokens > 0 && rangePrice.getCachedInput() != null) {
                totalCost = totalCost.add(calculateCost(inputDetails, "cached_tokens", cachedTokens, rangePrice.getCachedInput()));
                inputTokens -= cachedTokens;
            }

            if(inputTokens > 0) {
                totalCost = totalCost.add(calculateCost(inputDetails, "input_tokens", inputTokens, rangePrice.getInput()));
            }

            if(reasoningTokens != null && reasoningTokens > 0 && rangePrice.getReasoningOutput() != null) {
                totalCost = totalCost.add(calculateCost(outputDetails, "reasoning_tokens", reasoningTokens, rangePrice.getReasoningOutput()));
                outputTokens -= reasoningTokens;
            }

            if(outputTokens > 0) {
                totalCost = totalCost.add(calculateCost(outputDetails, "output_tokens", outputTokens, rangePrice.getOutput()));
            }

            if(price.getToolPrices() != null && toolUsage != null) {
                totalCost = totalCost.add(calculateToolCost(price.getToolPrices(), toolUsage, toolDetails));
            }

            return CostDetails.builder()
                    .totalCost(totalCost)
                    .inputDetails(inputDetails.isEmpty() ? null : inputDetails)
                    .outputDetails(outputDetails.isEmpty() ? null : outputDetails)
                    .toolDetails(toolDetails.isEmpty() ? null : toolDetails)
                    .build();
        }

        private BigDecimal calculateCost(Map<String, CostDetails.CostDetailItem> details, String type, int tokens, BigDecimal unitPrice) {
            BigDecimal cost = unitPrice.multiply(BigDecimal.valueOf(tokens / 1000.0));
            details.put(type, CostDetails.CostDetailItem.builder().tokens(tokens).unitPrice(unitPrice).cost(cost).build());
            return cost;
        }

        private BigDecimal calculateToolCost(
                Map<String, BigDecimal> toolPrices,
                Map<String, Integer> toolUsage,
                Map<String, CostDetails.ToolCostDetailItem> toolDetails) {
            BigDecimal toolCost = BigDecimal.ZERO;

            if(toolUsage == null || toolUsage.isEmpty()) {
                return toolCost;
            }

            for(Map.Entry<String, Integer> entry : toolUsage.entrySet()) {
                String toolName = entry.getKey();
                Integer count = entry.getValue();

                if(count == null || count == 0) {
                    continue;
                }

                BigDecimal unitPrice = toolPrices.get(toolName);
                if(unitPrice != null) {
                    BigDecimal cost = unitPrice.multiply(BigDecimal.valueOf(count));
                    toolCost = toolCost.add(cost);
                    toolDetails.put(toolName, CostDetails.ToolCostDetailItem.builder()
                            .callCount(count).unitPrice(unitPrice)
                            .cost(cost).build());
                }
            }

            return toolCost;
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            ResponsesPriceInfo price = JacksonUtils.deserialize(priceInfo, ResponsesPriceInfo.class);
            if(price == null) {
                return false;
            }
            return price.validate();
        }
    };

    interface EndpointCostCalculator {
        CostDetails calculate(String priceInfo, Object usage);

        boolean checkPriceInfo(String priceInfo);
    }
}

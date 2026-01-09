package com.ke.bella.openapi.protocol.cost;

import java.math.BigDecimal;
import java.util.Arrays;
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

    public static BigDecimal calculate(String endpoint, String priceInfo, Object usage) {
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
        public BigDecimal calculate(String priceInfo, Object usage) {
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
            Pair<BigDecimal, Integer> inputParts = CompletionsCalHelper.calculateAllElements(CompletionsCalHelper.INPUT, price,
                    tokenUsage.getPrompt_tokens_details());
            Pair<BigDecimal, Integer> outputParts = CompletionsCalHelper.calculateAllElements(CompletionsCalHelper.OUTPUT, price,
                    tokenUsage.getCompletion_tokens_details());
            if(inputParts.getLeft().doubleValue() > 0) {
                promptTokens -= inputParts.getRight();
            }
            if(outputParts.getLeft().doubleValue() > 0) {
                completionsTokens -= outputParts.getRight();
            }
            return price.getInput().multiply(BigDecimal.valueOf(promptTokens / 1000.0))
                    .add(price.getOutput().multiply(BigDecimal.valueOf(completionsTokens / 1000.0)))
                    .add(inputParts.getLeft()).add(outputParts.getLeft());
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            CompletionPriceInfo price = JacksonUtils.deserialize(priceInfo, CompletionPriceInfo.class);
            return price != null && price.getInput() != null && price.getOutput() != null;
        }
    };

    static EndpointCostCalculator embedding = new EndpointCostCalculator() {
        @Override
        public BigDecimal calculate(String priceInfo, Object usage) {
            EmbeddingPriceInfo price = JacksonUtils.deserialize(priceInfo, EmbeddingPriceInfo.class);
            EmbeddingResponse.TokenUsage tokenUsage = (EmbeddingResponse.TokenUsage) usage;
            return price.getInput().multiply(BigDecimal.valueOf(tokenUsage.getPrompt_tokens() / 1000.0));
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            EmbeddingPriceInfo price = JacksonUtils.deserialize(priceInfo, EmbeddingPriceInfo.class);
            return price != null && price.getInput() != null;
        }
    };

    static EndpointCostCalculator tts = new EndpointCostCalculator() {
        @Override
        public BigDecimal calculate(String priceInfo, Object usage) {
            TtsPriceInfo price = JacksonUtils.deserialize(priceInfo, TtsPriceInfo.class);
            int inputLength = (int) usage;
            return price.getInput().multiply(BigDecimal.valueOf(inputLength / 10000.0));
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            TtsPriceInfo price = JacksonUtils.deserialize(priceInfo, TtsPriceInfo.class);
            return price != null && price.getInput() != null;
        }
    };

    static EndpointCostCalculator asr_flash = new EndpointCostCalculator() {
        @Override
        public BigDecimal calculate(String priceInfo, Object usage) {
            FlashAsrPriceInfo price = JacksonUtils.deserialize(priceInfo, FlashAsrPriceInfo.class);
            return price.getPrice();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            FlashAsrPriceInfo price = JacksonUtils.deserialize(priceInfo, FlashAsrPriceInfo.class);
            return price != null && price.getPrice() != null;
        }
    };

    static EndpointCostCalculator realtime = new EndpointCostCalculator() {
        @Override
        public BigDecimal calculate(String priceInfo, Object usage) {
            RealTimePriceInfo price = JacksonUtils.deserialize(priceInfo, RealTimePriceInfo.class);
            return BigDecimal.valueOf((price.getPrice().doubleValue() / 3600 * 100) * Double.valueOf(usage.toString()));
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            RealTimePriceInfo price = JacksonUtils.deserialize(priceInfo, RealTimePriceInfo.class);
            return price != null && price.getPrice() != null;
        }
    };

    static EndpointCostCalculator asr_transcriptions = new EndpointCostCalculator() {
        @Override
        public BigDecimal calculate(String priceInfo, Object usage) {
            // Assuming usage is the duration in seconds
            TranscriptionsAsrPriceInfo price = JacksonUtils.deserialize(priceInfo, TranscriptionsAsrPriceInfo.class);
            return BigDecimal.valueOf((price.getPrice().doubleValue() / 3600 * 100) * Double.valueOf(usage.toString()));
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            TranscriptionsAsrPriceInfo price = JacksonUtils.deserialize(priceInfo, TranscriptionsAsrPriceInfo.class);
            return price != null && price.getPrice() != null;
        }
    };

    static EndpointCostCalculator images = new EndpointCostCalculator() {
        @Override
        public BigDecimal calculate(String priceInfo, Object usage) {
            ImagesPriceInfo price = JacksonUtils.deserialize(priceInfo, ImagesPriceInfo.class);
            ImagesResponse.Usage imageUsage = (ImagesResponse.Usage) usage;
            BigDecimal imageCost = BigDecimal.ZERO;
            // 获取质量和尺寸信息
            String quality = imageUsage.getQuality();
            String size = imageUsage.getSize();
            int imageCount = imageUsage.getNum();
            if(price != null && CollectionUtils.isNotEmpty(price.getDetails())) {
                ImagesPriceInfo.ImagesPriceInfoDetails details = price.getDetails().
                        stream().filter(d -> d.getSize().equals(size)).findAny().orElse(price.getDetails().get(0));

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
            return imageCost;
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
        public BigDecimal calculate(String priceInfo, Object usage) {
            ImagesEditsPriceInfo price = JacksonUtils.deserialize(priceInfo, ImagesEditsPriceInfo.class);
            ImagesResponse.Usage imageEditsUsage = (ImagesResponse.Usage) usage;
            BigDecimal editCost = BigDecimal.ZERO;

            if(price != null) {

                int editCount = imageEditsUsage.getNum();
                if(price.getPricePerEdit() != null) {
                    editCost = editCost.add(price.getPricePerEdit().multiply(BigDecimal.valueOf(editCount)));
                }

                if(price.getImageTokenPrice() != null && imageEditsUsage.getTotal_tokens() != null) {
                    BigDecimal imageTokenCost = price.getImageTokenPrice()
                            .multiply(BigDecimal.valueOf(imageEditsUsage.getTotal_tokens() / 1000.0));
                    editCost = editCost.add(imageTokenCost);
                }
            }

            return editCost;
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {

            ImagesEditsPriceInfo price = JacksonUtils.deserialize(priceInfo, ImagesEditsPriceInfo.class);
            return price != null && price.getPricePerEdit() != null;
        }
    };

    static EndpointCostCalculator speakerEmbedding = new EndpointCostCalculator() {
        @Override
        public BigDecimal calculate(String priceInfo, Object usage) {
            SpeakerEmbeddingPriceInfo priceConfig = JacksonUtils.deserialize(priceInfo, SpeakerEmbeddingPriceInfo.class);
            if(priceConfig.getPrice() == null) {
                return BigDecimal.ZERO;
            }

            // usage可能是Double类型(duration秒数)或者SpeakerEmbeddingUsage类型
            double durationSeconds = 0.0;
            if(usage instanceof Double) {
                durationSeconds = (Double) usage;
            } else if(usage instanceof SpeakerEmbeddingLogHandler.SpeakerEmbeddingUsage) {
                SpeakerEmbeddingLogHandler.SpeakerEmbeddingUsage speakerUsage =
                        (SpeakerEmbeddingLogHandler.SpeakerEmbeddingUsage) usage;
                durationSeconds = speakerUsage.getDurationSeconds();
            } else if(usage != null) {
                // 尝试解析为数字
                try {
                    durationSeconds = Double.valueOf(usage.toString());
                } catch (NumberFormatException e) {
                    return BigDecimal.ZERO;
                }
            }

            // 基于时长计算成本：price（元/小时） × duration（小时）
            // 将秒转换为小时：durationSeconds / 3600
            double durationHours = durationSeconds / 3600.0;
            return priceConfig.getPrice().multiply(BigDecimal.valueOf(durationHours));
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
        public BigDecimal calculate(String priceInfo, Object usage) {
            SpeakerDiarizationPriceInfo priceConfig = JacksonUtils.deserialize(priceInfo, SpeakerDiarizationPriceInfo.class);
            if(priceConfig.getPrice() == null) {
                return BigDecimal.ZERO;
            }

            // usage 就是 SpeakerDiarizationUsage 类型
            if(usage instanceof SpeakerDiarizationLogHandler.SpeakerDiarizationUsage) {
                SpeakerDiarizationLogHandler.SpeakerDiarizationUsage diarizationUsage =
                        (SpeakerDiarizationLogHandler.SpeakerDiarizationUsage) usage;
                int audioDurationSeconds = diarizationUsage.getAudioDurationSeconds();

                // 基于音频时长计算成本：price（元/小时） × audioDurationHours（小时）
                // 将秒转换为小时：audioDurationSeconds / 3600.0
                double audioDurationHours = audioDurationSeconds / 3600.0;
                return priceConfig.getPrice().multiply(BigDecimal.valueOf(audioDurationHours));
            }

            return BigDecimal.ZERO;
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
        public BigDecimal calculate(String priceInfo, Object usage) {
            WebSearchPriceInfo price = JacksonUtils.deserialize(priceInfo, WebSearchPriceInfo.class);
            WebSearchUsage searchUsage = (WebSearchUsage) usage;

            // Determine search depth from usage and calculate cost
            // Basic Search: 1 API credit per request
            // Advanced Search: 2 API credits per request
            String searchDepth = searchUsage.getSearchDepth();
            if("advanced".equalsIgnoreCase(searchDepth)) {
                return price.getAdvancedSearchPrice();
            } else {
                return price.getBasicSearchPrice();
            }
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            WebSearchPriceInfo price = JacksonUtils.deserialize(priceInfo, WebSearchPriceInfo.class);
            return price != null && price.getBasicSearchPrice() != null && price.getAdvancedSearchPrice() != null;
        }
    };

    static EndpointCostCalculator webCrawl = new EndpointCostCalculator() {
        @Override
        public BigDecimal calculate(String priceInfo, Object usage) {
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

            return totalCost;
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
        public BigDecimal calculate(String priceInfo, Object usage) {
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

            return totalCost;
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
        public BigDecimal calculate(String priceInfo, Object usage) {
            OcrPriceInfo price = JacksonUtils.deserialize(priceInfo, OcrPriceInfo.class);
            int times = Integer.parseInt(usage.toString());
            return price.getPricePerRequest().multiply(BigDecimal.valueOf(times));
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            OcrPriceInfo price = JacksonUtils.deserialize(priceInfo, OcrPriceInfo.class);
            return price != null && price.getPricePerRequest() != null;
        }
    };

    static EndpointCostCalculator video = new EndpointCostCalculator() {
        @Override
        public BigDecimal calculate(String priceInfo, Object usage) {
            VideoPriceInfo price = JacksonUtils.deserialize(priceInfo, VideoPriceInfo.class);
            VideoUsage videoUsage;
            if(usage instanceof VideoUsage) {
                videoUsage = (VideoUsage) usage;
            } else {
                String usageJson = JacksonUtils.serialize(usage);
                videoUsage = JacksonUtils.deserialize(usageJson, VideoUsage.class);
            }

            int completionTokens = videoUsage.getCompletion_tokens() != null ? videoUsage.getCompletion_tokens() : 0;
            return price.getOutput().multiply(BigDecimal.valueOf(completionTokens / 1000.0));
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            VideoPriceInfo price = JacksonUtils.deserialize(priceInfo, VideoPriceInfo.class);
            return price != null && price.getOutput() != null;
        }
    };

    interface EndpointCostCalculator {
        BigDecimal calculate(String priceInfo, Object usage);

        boolean checkPriceInfo(String priceInfo);
    }
}

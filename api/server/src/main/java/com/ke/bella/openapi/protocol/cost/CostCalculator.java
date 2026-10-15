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
import com.ke.bella.openapi.protocol.asr.flash.FlashAsrBillingMode;
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
import com.ke.bella.openapi.protocol.live.LivePriceInfo;
import com.ke.bella.openapi.protocol.live.LiveSessionUsage;
import com.ke.bella.openapi.protocol.ocr.OcrPriceInfo;
import com.ke.bella.openapi.protocol.realtime.RealTimePriceInfo;
import com.ke.bella.openapi.protocol.rerank.RerankPriceInfo;
import com.ke.bella.openapi.protocol.rerank.RerankResponse;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingLogHandler;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingPriceInfo;
import com.ke.bella.openapi.protocol.tts.TtsPriceInfo;
import com.ke.bella.openapi.protocol.video.VideoPriceInfo;
import com.ke.bella.openapi.protocol.video.VideoUsage;
import com.ke.bella.openapi.protocol.systemone.SystemOnePriceInfo;
import com.ke.bella.openapi.protocol.systemone.SystemOneUsage;
import com.ke.bella.openapi.protocol.web.WebCrawlPriceInfo;
import com.ke.bella.openapi.protocol.web.WebCrawlUsage;
import com.ke.bella.openapi.protocol.web.WebExtractPriceInfo;
import com.ke.bella.openapi.protocol.web.WebExtractUsage;
import com.ke.bella.openapi.protocol.web.WebSearchPriceInfo;
import com.ke.bella.openapi.protocol.web.WebSearchUsage;
import com.ke.bella.openapi.protocol.alpha.search.AlphaSearchPriceInfo;
import com.ke.bella.openapi.protocol.alpha.search.AlphaSearchUsage;
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
        RERANK("/v*/reranks", rerank),
        SYSTEM_ONE("/v*/systemone", systemOne),
        TTS("/v*/audio/speech", tts),
        REALTIME_TTS("/v*/audio/tts/stream", tts),
        ASR_FLASH("/v*/audio/asr/flash", asr_flash),
        ASR_STREAM("/v*/audio/asr/stream", realtime),
        REAL_TIME("/v*/audio/realtime", realtime),
        LIVE_SESSION("/v*/live/sessions", live),
        ASR_TRANSCRIPTIONS("/v*/audio/transcriptions", asr_transcriptions),
        ASR_TRANSCRIPTIONS_FILE("/v*/audio/transcriptions/file", asr_transcriptions),
        SPEAKER_EMBEDDING("/v*/audio/speaker/embedding", speakerEmbedding),
        SPEAKER_DIARIZATION("/v*/audio/speaker/diarization", speakerDiarization),
        IMAGES("/v*/images/generations", images),
        IMAGES_EDITS("/v*/images/edits", images_edits),
        IMAGES_VARIATIONS("/v*/images/variations", images),
        WEB_SEARCH("/v*/web/search", webSearch),
        WEB_CRAWL("/v*/web/crawl", webCrawl),
        WEB_EXTRACT("/v*/web/extract", webExtract),
        ALPHA_SEARCH("/v*/alpha/search", alphaSearch),
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

            Map<String, CostDetails.ToolCostDetailItem> toolDetails = new HashMap<>();
            if(price.getToolPrices() != null && tokenUsage.getTool_usage() != null) {
                totalCost = totalCost.add(calculateToolCost(price.getToolPrices(), tokenUsage.getTool_usage(), toolDetails));
            }

            return CostDetails.builder()
                    .totalCost(totalCost)
                    .inputDetails(inputDetails.isEmpty() ? null : inputDetails)
                    .outputDetails(outputDetails.isEmpty() ? null : outputDetails)
                    .toolDetails(toolDetails.isEmpty() ? null : toolDetails)
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

    static EndpointCostCalculator alphaSearch = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            AlphaSearchPriceInfo price = JacksonUtils.deserialize(priceInfo, AlphaSearchPriceInfo.class);
            AlphaSearchUsage requestUsage = usage instanceof AlphaSearchUsage
                    ? (AlphaSearchUsage) usage
                    : JacksonUtils.deserialize(JacksonUtils.serialize(usage), AlphaSearchUsage.class);
            int count = requestUsage == null ? 0 : requestUsage.getRequestCount();
            BigDecimal cost = price.getPricePerRequest()
                    .multiply(BigDecimal.valueOf(count))
                    .multiply(BigDecimal.valueOf(price.getSupplierDiscount()));
            return CostDetails.builder().totalCost(cost).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            AlphaSearchPriceInfo price = JacksonUtils.deserialize(priceInfo, AlphaSearchPriceInfo.class);
            return price != null && price.validate();
        }
    };

    static EndpointCostCalculator rerank = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            RerankPriceInfo price = JacksonUtils.deserialize(priceInfo, RerankPriceInfo.class);
            RerankResponse.Usage tokenUsage;
            if(usage instanceof RerankResponse.Usage) {
                tokenUsage = (RerankResponse.Usage) usage;
            } else {
                tokenUsage = JacksonUtils.deserialize(JacksonUtils.serialize(usage), RerankResponse.Usage.class);
            }
            int totalTokens = tokenUsage == null || tokenUsage.getTotalTokens() == null ? 0 : tokenUsage.getTotalTokens();
            BigDecimal cost = price.getInput().multiply(BigDecimal.valueOf(totalTokens / 1000.0));
            Map<String, CostDetails.CostDetailItem> inputDetails = new HashMap<>();
            if(totalTokens > 0) {
                inputDetails.put("total_tokens", CostDetails.CostDetailItem.builder()
                        .tokens(totalTokens)
                        .unitPrice(price.getInput())
                        .cost(cost)
                        .build());
            }
            return CostDetails.builder()
                    .totalCost(cost)
                    .inputDetails(inputDetails.isEmpty() ? null : inputDetails)
                    .build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            RerankPriceInfo price = JacksonUtils.deserialize(priceInfo, RerankPriceInfo.class);
            return price != null && price.getInput() != null;
        }
    };

    static EndpointCostCalculator systemOne = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            SystemOnePriceInfo price = JacksonUtils.deserialize(priceInfo, SystemOnePriceInfo.class);
            if(price == null || price.getInput() == null || usage == null) {
                return CostDetails.builder().totalCost(BigDecimal.ZERO).build();
            }
            SystemOneUsage tokenUsage = usage instanceof SystemOneUsage
                    ? (SystemOneUsage) usage
                    : JacksonUtils.deserialize(JacksonUtils.serialize(usage), SystemOneUsage.class);
            Integer totalTokens = tokenUsage == null ? null : tokenUsage.getTotalTokens();
            if(totalTokens == null) {
                return CostDetails.builder().totalCost(BigDecimal.ZERO).build();
            }
            BigDecimal tokenUnits = BigDecimal.valueOf(totalTokens).divide(BigDecimal.valueOf(1000));
            return CostDetails.builder().totalCost(price.getInput().multiply(tokenUnits)).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            SystemOnePriceInfo price = JacksonUtils.deserialize(priceInfo, SystemOnePriceInfo.class);
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
            if(price.getBillingMode() == FlashAsrBillingMode.duration) {
                BigDecimal durationSeconds = BigDecimal.valueOf(resolveFlashAsrDurationSeconds(usage));
                BigDecimal totalCost = price.getPrice()
                        .multiply(BigDecimal.valueOf(100))
                        .multiply(durationSeconds)
                        .divide(BigDecimal.valueOf(3600), 10, java.math.RoundingMode.HALF_UP);
                return CostDetails.builder().totalCost(totalCost).build();
            }
            return CostDetails.builder().totalCost(price.getPrice()).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            FlashAsrPriceInfo price = JacksonUtils.deserialize(priceInfo, FlashAsrPriceInfo.class);
            return price != null && price.getPrice() != null;
        }
    };

    private static double resolveFlashAsrDurationSeconds(Object usage) {
        if(usage instanceof Map) {
            Object duration = ((Map<?, ?>) usage).get("duration");
            return toDouble(duration);
        }
        return toDouble(usage);
    }

    private static double toDouble(Object value) {
        if(value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if(value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

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

    static EndpointCostCalculator live = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            LivePriceInfo price = JacksonUtils.deserialize(priceInfo, LivePriceInfo.class);
            LiveSessionUsage liveUsage = usage instanceof LiveSessionUsage ? (LiveSessionUsage) usage
                    : JacksonUtils.deserialize(JacksonUtils.serialize(usage), LiveSessionUsage.class);
            if(liveUsage == null) {
                liveUsage = new LiveSessionUsage();
            }
            BigDecimal discount = BigDecimal.valueOf(price.getSupplierDiscount());
            Map<String, CostDetails.CostDetailItem> inputDetails = new HashMap<>();
            Map<String, CostDetails.CostDetailItem> outputDetails = new HashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            total = total.add(liveTokenCost(inputDetails, "input_text_tokens", liveUsage.getInputTextTokens(),
                    price.getInputTextTokenPrice(), discount));
            total = total.add(liveTokenCost(inputDetails, "input_audio_tokens", liveUsage.getInputAudioTokens(),
                    price.getInputAudioTokenPrice(), discount));
            total = total.add(liveTokenCost(inputDetails, "input_video_tokens", liveUsage.getInputVideoTokens(),
                    price.getInputVideoTokenPrice(), discount));
            total = total.add(liveTokenCost(inputDetails, "cached_tokens", liveUsage.getCachedTokens(),
                    price.getCachedTokenPrice(), discount));
            total = total.add(liveTokenCost(outputDetails, "output_text_tokens", liveUsage.getOutputTextTokens(),
                    price.getOutputTextTokenPrice(), discount));
            total = total.add(liveTokenCost(outputDetails, "output_audio_tokens", liveUsage.getOutputAudioTokens(),
                    price.getOutputAudioTokenPrice(), discount));

            Long durationSeconds = liveUsage.getSessionDurationSeconds();
            if(price.getSessionMinutePrice() != null && durationSeconds != null && durationSeconds > 0) {
                BigDecimal durationCost = price.getSessionMinutePrice()
                        .multiply(BigDecimal.valueOf(durationSeconds))
                        .divide(BigDecimal.valueOf(60), 10, java.math.RoundingMode.HALF_UP)
                        .multiply(discount);
                total = total.add(durationCost);
                inputDetails.put("session_duration_seconds", CostDetails.CostDetailItem.builder()
                        .quantity(safeInt(durationSeconds)).unitPrice(price.getSessionMinutePrice()).cost(durationCost).build());
            }

            Map<String, CostDetails.ToolCostDetailItem> toolDetails = new HashMap<>();
            Long toolCalls = liveUsage.getToolCalls();
            if(price.getToolCallPrice() != null && toolCalls != null && toolCalls > 0) {
                BigDecimal toolCost = price.getToolCallPrice().multiply(BigDecimal.valueOf(toolCalls)).multiply(discount);
                total = total.add(toolCost);
                toolDetails.put("tool_calls", CostDetails.ToolCostDetailItem.builder()
                        .callCount(safeInt(toolCalls)).unitPrice(price.getToolCallPrice()).cost(toolCost).build());
            }
            return CostDetails.builder().totalCost(total)
                    .inputDetails(inputDetails.isEmpty() ? null : inputDetails)
                    .outputDetails(outputDetails.isEmpty() ? null : outputDetails)
                    .toolDetails(toolDetails.isEmpty() ? null : toolDetails)
                    .build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            LivePriceInfo price = JacksonUtils.deserialize(priceInfo, LivePriceInfo.class);
            if(price == null || price.getSupplierDiscount() <= 0 || price.getSupplierDiscount() > 1) {
                return false;
            }
            BigDecimal[] prices = { price.getInputTextTokenPrice(), price.getInputAudioTokenPrice(), price.getInputVideoTokenPrice(),
                    price.getOutputTextTokenPrice(), price.getOutputAudioTokenPrice(), price.getCachedTokenPrice(),
                    price.getSessionMinutePrice(), price.getToolCallPrice() };
            boolean configured = false;
            for (BigDecimal configuredPrice : prices) {
                if(configuredPrice != null) {
                    configured = true;
                    if(configuredPrice.compareTo(BigDecimal.ZERO) < 0) {
                        return false;
                    }
                }
            }
            return configured;
        }
    };

    private static BigDecimal liveTokenCost(Map<String, CostDetails.CostDetailItem> details, String key, Long tokens,
            BigDecimal unitPrice, BigDecimal discount) {
        if(unitPrice == null || tokens == null || tokens <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = unitPrice.multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1_000_000), 10, java.math.RoundingMode.HALF_UP)
                .multiply(discount);
        details.put(key, CostDetails.CostDetailItem.builder().tokens(safeInt(tokens)).unitPrice(unitPrice).cost(cost).build());
        return cost;
    }

    private static int safeInt(Long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

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
            ImagesResponse.Usage imageUsage;
            if(usage instanceof ImagesResponse.Usage) {
                imageUsage = (ImagesResponse.Usage) usage;
            } else {
                imageUsage = JacksonUtils.deserialize(JacksonUtils.serialize(usage), ImagesResponse.Usage.class);
            }
            if(imageUsage == null) {
                imageUsage = new ImagesResponse.Usage();
            }
            BigDecimal imageCost = BigDecimal.ZERO;
            Map<String, CostDetails.CostDetailItem> inputDetails = new HashMap<>();
            Map<String, CostDetails.CostDetailItem> outputDetails = new HashMap<>();
            // 获取质量和尺寸信息
            String quality = imageUsage.getQuality();
            String size = imageUsage.getSize();
            Integer imageCount = imageUsage.getNum();
            if(price != null && CollectionUtils.isNotEmpty(price.getDetails())) {
                ImagesPriceInfo.ImagesPriceInfoDetails details = price.getDetails().stream().filter(d -> StringUtils.equals(d.getSize(), size)).findAny()
                        .orElse(price.getDetails().get(0));

                if(StringUtils.isBlank(price.getBillingMode())) {
                    if(details.getImageTokenPrice() != null || details.getTextTokenPrice() != null) {
                        if(imageUsage.getInput_tokens_details() != null) {
                            BigDecimal textTokenCost = calculateTokenCost(inputDetails, "text_tokens",
                                    imageUsage.getInput_tokens_details().getText_tokens(), details.getTextTokenPrice());
                            BigDecimal imageTokenCost = calculateTokenCost(inputDetails, "image_tokens",
                                    imageUsage.getInput_tokens_details().getImage_tokens(), details.getImageTokenPrice());
                            imageCost = imageCost.add(textTokenCost).add(imageTokenCost);
                        }
                    }
                    imageCost = imageCost.add(calculatePerImageCost(details, quality, imageCount, outputDetails));
                    return buildImageCostDetails(imageCost, inputDetails, outputDetails);
                }

                String billingMode = price.getBillingMode().toLowerCase();
                if("token".equals(billingMode) || "mixed".equals(billingMode)) {
                    imageCost = imageCost.add(calculateImageTokenCost(details, imageUsage, inputDetails, outputDetails));
                }
                if("per_image".equals(billingMode) || "mixed".equals(billingMode)) {
                    imageCost = imageCost.add(calculatePerImageCost(details, normalizeImageQuality(quality), resolveImageCount(imageUsage), outputDetails));
                }
            }
            return buildImageCostDetails(imageCost, inputDetails, outputDetails);
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            ImagesPriceInfo price = JacksonUtils.deserialize(priceInfo, ImagesPriceInfo.class);

            if(price == null || CollectionUtils.isEmpty(price.getDetails())) {
                return false;
            }
            if(StringUtils.isBlank(price.getBillingMode())) {
                return price.getDetails().stream().allMatch(CostCalculator::hasPerImagePrices);
            }
            String billingMode = price.getBillingMode().toLowerCase();
            if(!"per_image".equals(billingMode) && !"token".equals(billingMode) && !"mixed".equals(billingMode)) {
                return false;
            }
            if("token".equals(billingMode)) {
                return price.getDetails().stream().allMatch(CostCalculator::hasImageOutputTokenPrice);
            }
            if("per_image".equals(billingMode)) {
                return price.getDetails().stream().allMatch(CostCalculator::hasPerImagePrices);
            }
            return price.getDetails().stream().allMatch(d -> hasPerImagePrices(d) && hasImageOutputTokenPrice(d));
        }
    };

    private static BigDecimal calculateImageTokenCost(ImagesPriceInfo.ImagesPriceInfoDetails details, ImagesResponse.Usage imageUsage,
            Map<String, CostDetails.CostDetailItem> inputDetails, Map<String, CostDetails.CostDetailItem> outputDetails) {
        return calculateImageTokenCost(details.getTextTokenPrice(), details.getImageTokenPrice(), details.getImageInputTokenPrice(),
                details.getImageOutputTokenPrice(), imageUsage, inputDetails, outputDetails);
    }

    private static BigDecimal calculateImageTokenCost(ImagesEditsPriceInfo price, ImagesResponse.Usage imageUsage,
            Map<String, CostDetails.CostDetailItem> inputDetails, Map<String, CostDetails.CostDetailItem> outputDetails) {
        return calculateImageTokenCost(price.getTextTokenPrice(), price.getImageTokenPrice(), price.getImageInputTokenPrice(),
                price.getImageOutputTokenPrice(), imageUsage, inputDetails, outputDetails);
    }

    private static BigDecimal calculateImageTokenCost(BigDecimal textTokenPrice, BigDecimal imageTokenPrice, BigDecimal imageInputTokenPrice,
            BigDecimal imageOutputTokenPrice, ImagesResponse.Usage imageUsage, Map<String, CostDetails.CostDetailItem> inputDetails,
            Map<String, CostDetails.CostDetailItem> outputDetails) {
        BigDecimal tokenCost = BigDecimal.ZERO;
        if(imageUsage.getInput_tokens_details() != null) {
            BigDecimal textTokenCost = calculateTokenCost(inputDetails, "text_tokens",
                    imageUsage.getInput_tokens_details().getText_tokens(), textTokenPrice);
            BigDecimal imageInputTokenCost = calculateTokenCost(inputDetails, "image_tokens",
                    imageUsage.getInput_tokens_details().getImage_tokens(), Optional.ofNullable(imageInputTokenPrice).orElse(imageTokenPrice));
            tokenCost = tokenCost.add(textTokenCost).add(imageInputTokenCost);
        }
        BigDecimal imageOutputTokenCost = calculateTokenCost(outputDetails, "image_tokens", imageUsage.getOutput_tokens(),
                Optional.ofNullable(imageOutputTokenPrice).orElse(imageTokenPrice));
        return tokenCost.add(imageOutputTokenCost);
    }

    private static BigDecimal calculateTokenCost(Map<String, CostDetails.CostDetailItem> details, String type, Integer tokens,
            BigDecimal unitPrice) {
        if(tokens == null || tokens <= 0 || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = unitPrice.multiply(BigDecimal.valueOf(tokens / 1000.0));
        details.put(type, CostDetails.CostDetailItem.builder()
                .tokens(tokens)
                .unitPrice(unitPrice)
                .cost(cost)
                .build());
        return cost;
    }

    private static BigDecimal calculateQuantityCost(Map<String, CostDetails.CostDetailItem> details, String type, Integer quantity,
            BigDecimal unitPrice) {
        if(quantity == null || quantity <= 0 || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = unitPrice.multiply(BigDecimal.valueOf(quantity));
        details.put(type, CostDetails.CostDetailItem.builder()
                .quantity(quantity)
                .unitPrice(unitPrice)
                .cost(cost)
                .build());
        return cost;
    }

    private static CostDetails buildImageCostDetails(BigDecimal totalCost, Map<String, CostDetails.CostDetailItem> inputDetails,
            Map<String, CostDetails.CostDetailItem> outputDetails) {
        return CostDetails.builder()
                .totalCost(totalCost)
                .inputDetails(inputDetails.isEmpty() ? null : inputDetails)
                .outputDetails(outputDetails.isEmpty() ? null : outputDetails)
                .build();
    }

    private static BigDecimal calculatePerImageCost(ImagesPriceInfo.ImagesPriceInfoDetails details, String quality, Integer imageCount,
            Map<String, CostDetails.CostDetailItem> outputDetails) {
        int count = imageCount == null ? 1 : imageCount;
        BigDecimal unitPrice;
        if("low".equals(quality)) {
            unitPrice = details.getLdPricePerImage();
        } else if("medium".equals(quality)) {
            unitPrice = details.getMdPricePerImage();
        } else {
            unitPrice = details.getHdPricePerImage();
        }
        return calculateQuantityCost(outputDetails, "images", count, unitPrice);
    }

    private static String normalizeImageQuality(String quality) {
        if(quality == null) {
            return "high";
        }
        switch (quality.toLowerCase()) {
        case "hd":
        case "high":
            return "high";
        case "standard":
        case "medium":
            return "medium";
        case "low":
            return "low";
        default:
            return quality;
        }
    }

    private static int resolveImageCount(ImagesResponse.Usage imageUsage) {
        if(imageUsage.getNum() != null) {
            return imageUsage.getNum();
        }
        return 1;
    }

    private static boolean hasPerImagePrices(ImagesPriceInfo.ImagesPriceInfoDetails details) {
        return StringUtils.isNotBlank(details.getSize()) &&
                details.getHdPricePerImage() != null &&
                details.getMdPricePerImage() != null &&
                details.getLdPricePerImage() != null;
    }

    private static boolean hasImageOutputTokenPrice(ImagesPriceInfo.ImagesPriceInfoDetails details) {
        return details.getImageOutputTokenPrice() != null ||
                details.getImageTokenPrice() != null;
    }

    private static boolean hasImageOutputTokenPrice(ImagesEditsPriceInfo price) {
        return price.getImageOutputTokenPrice() != null ||
                price.getImageTokenPrice() != null;
    }

    static EndpointCostCalculator images_edits = new EndpointCostCalculator() {
        @Override
        public CostDetails calculate(String priceInfo, Object usage) {
            ImagesEditsPriceInfo price = JacksonUtils.deserialize(priceInfo, ImagesEditsPriceInfo.class);
            ImagesResponse.Usage imageEditsUsage;
            if(usage instanceof ImagesResponse.Usage) {
                imageEditsUsage = (ImagesResponse.Usage) usage;
            } else {
                imageEditsUsage = JacksonUtils.deserialize(JacksonUtils.serialize(usage), ImagesResponse.Usage.class);
            }
            if(imageEditsUsage == null) {
                imageEditsUsage = new ImagesResponse.Usage();
            }
            BigDecimal editCost = BigDecimal.ZERO;
            Map<String, CostDetails.CostDetailItem> inputDetails = new HashMap<>();
            Map<String, CostDetails.CostDetailItem> outputDetails = new HashMap<>();

            if(price != null && StringUtils.isNotBlank(price.getBillingMode())) {
                String billingMode = price.getBillingMode().toLowerCase();
                if("token".equals(billingMode) || "mixed".equals(billingMode)) {
                    editCost = editCost.add(calculateImageTokenCost(price, imageEditsUsage, inputDetails, outputDetails));
                }
                if("per_image".equals(billingMode) || "mixed".equals(billingMode)) {
                    editCost = editCost.add(calculateQuantityCost(outputDetails, "images", resolveImageCount(imageEditsUsage),
                            price.getPricePerEdit()));
                }
                return buildImageCostDetails(editCost, inputDetails, outputDetails);
            }

            if(price != null && (price.getPricePerEdit() != null || price.getImageTokenPrice() != null)) {
                if(price.getPricePerEdit() != null) {
                    editCost = editCost.add(calculateQuantityCost(outputDetails, "images", resolveImageCount(imageEditsUsage),
                            price.getPricePerEdit()));
                }

                if(price.getImageTokenPrice() != null && imageEditsUsage.getTotal_tokens() != null) {
                    BigDecimal imageTokenCost = calculateTokenCost(inputDetails, "total_tokens", imageEditsUsage.getTotal_tokens(),
                            price.getImageTokenPrice());
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
                        editCost = calculateTokenCost(outputDetails, "total_tokens", imageEditsUsage.getTotal_tokens(), imageOutput);
                    }
                }
            }

            return buildImageCostDetails(editCost, inputDetails, outputDetails);
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            ImagesEditsPriceInfo price = JacksonUtils.deserialize(priceInfo, ImagesEditsPriceInfo.class);
            if(price == null) {
                return false;
            }
            if(StringUtils.isBlank(price.getBillingMode())) {
                return price.getPricePerEdit() != null;
            }
            String billingMode = price.getBillingMode().toLowerCase();
            if(!"per_image".equals(billingMode) && !"token".equals(billingMode) && !"mixed".equals(billingMode)) {
                return false;
            }
            if("token".equals(billingMode)) {
                return hasImageOutputTokenPrice(price);
            }
            if("per_image".equals(billingMode)) {
                return price.getPricePerEdit() != null;
            }
            return price.getPricePerEdit() != null && hasImageOutputTokenPrice(price);
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

            // 基于时长计算成本：price（分/小时） × duration（小时）
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

                // 基于音频时长计算成本：price（分/小时） × audioDurationHours（小时）
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
            BigDecimal cost;
            if(price.getRequestPrice() != null) {
                cost = price.getRequestPrice().multiply(BigDecimal.valueOf(searchUsage.getRequestCount()));
            } else {
                cost = "advanced".equalsIgnoreCase(searchDepth) ? price.getAdvancedSearchPrice() : price.getBasicSearchPrice();
            }
            return CostDetails.builder().totalCost(cost).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            WebSearchPriceInfo price = JacksonUtils.deserialize(priceInfo, WebSearchPriceInfo.class);
            return price != null && (price.getRequestPrice() != null ||
                    (price.getBasicSearchPrice() != null && price.getAdvancedSearchPrice() != null));
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

            if(price.isMiniMaxBillingMode()) {
                if(videoUsage == null) {
                    return CostDetails.builder().totalCost(BigDecimal.ZERO).build();
                }
                double billedVideoSeconds = Optional.ofNullable(videoUsage.getInputSeconds()).orElse(0D)
                        + Optional.ofNullable(videoUsage.getOutputSeconds()).orElse(0D);
                BigDecimal videoCost = price.matchPricePerSecond(videoUsage)
                        .multiply(BigDecimal.valueOf(billedVideoSeconds));
                int billableImages = Math.max(0, Optional.ofNullable(videoUsage.getImageCount()).orElse(0)
                        - Optional.ofNullable(price.getFreeInputImageCount()).orElse(0));
                BigDecimal imageCost = Optional.ofNullable(price.getInputImagePricePerImage())
                        .orElse(BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(billableImages));
                BigDecimal audioCost = Optional.ofNullable(price.getInputAudioPricePerSecond())
                        .orElse(BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(Optional.ofNullable(videoUsage.getInputAudioSeconds()).orElse(0D)));
                return CostDetails.builder().totalCost(videoCost.add(imageCost).add(audioCost).multiply(BigDecimal.valueOf(price.getSupplierDiscount()))).build();
            } else if(price.isDurationBillingMode()) {
                if(videoUsage == null || videoUsage.getDuration() == null || videoUsage.getVideoCount() == null) {
                    log.warn("Video duration billing skipped because usage is incomplete: {}", usage);
                    return CostDetails.builder().totalCost(BigDecimal.ZERO).build();
                }
                BigDecimal totalCost = price.matchPricePerSecond(videoUsage)
                        .multiply(BigDecimal.valueOf(videoUsage.getDuration()))
                        .multiply(BigDecimal.valueOf(videoUsage.getVideoCount()))
                        .multiply(BigDecimal.valueOf(price.getSupplierDiscount()));
                return CostDetails.builder().totalCost(totalCost).build();
            } else if(price.isGeminiBillingMode()) {
                Map<String, CostDetails.CostDetailItem> inputDetails = new HashMap<>();
                Map<String, CostDetails.CostDetailItem> outputDetails = new HashMap<>();
                BigDecimal promptCost = calculateTokenCost(inputDetails, "prompt_tokens", videoUsage.getPromptTokens(), price.getInput());
                BigDecimal textOutputCost = calculateTokenCost(outputDetails, "text_output_tokens", videoUsage.getTextOutputTokens(), price.getTextOutput());
                BigDecimal videoOutputCost = calculateTokenCost(outputDetails, "video_output_tokens", videoUsage.getVideoOutputTokens(), price.getVideoOutput());
                return CostDetails.builder()
                        .totalCost(promptCost.add(textOutputCost).add(videoOutputCost))
                        .inputDetails(inputDetails.isEmpty() ? null : inputDetails)
                        .outputDetails(outputDetails.isEmpty() ? null : outputDetails)
                        .build();
            }

            int completionTokens = videoUsage.getCompletionTokens() != null ? videoUsage.getCompletionTokens() : 0;
            return CostDetails.builder().totalCost(price.getOutput().multiply(BigDecimal.valueOf(completionTokens / 1000.0))).build();
        }

        @Override
        public boolean checkPriceInfo(String priceInfo) {
            VideoPriceInfo price = JacksonUtils.deserialize(priceInfo, VideoPriceInfo.class);
            if(price == null) {
                return false;
            }
            if(price.isMiniMaxBillingMode()) {
                return price.hasValidDurationPrice();
            }
            if(price.isDurationBillingMode()) {
                return price.hasValidDurationPrice();
            }
            if(price.isGeminiBillingMode()) {
                return price.getInput() != null && price.getTextOutput() != null && price.getVideoOutput() != null;
            }
            return price.getOutput() != null;
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
            return CostCalculator.calculateToolCost(toolPrices, toolUsage, toolDetails);
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

    static BigDecimal calculateToolCost(
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

    interface EndpointCostCalculator {
        CostDetails calculate(String priceInfo, Object usage);

        boolean checkPriceInfo(String priceInfo);
    }
}

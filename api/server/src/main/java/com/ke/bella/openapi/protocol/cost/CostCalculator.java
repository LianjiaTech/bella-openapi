package com.ke.bella.openapi.protocol.cost;

import com.ke.bella.openapi.protocol.asr.flash.FlashAsrPriceInfo;
import com.ke.bella.openapi.protocol.asr.transcription.TranscriptionsAsrPriceInfo;
import com.ke.bella.openapi.protocol.realtime.RealTimePriceInfo;
import com.ke.bella.openapi.protocol.completion.CompletionPriceInfo;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.embedding.EmbeddingPriceInfo;
import com.ke.bella.openapi.protocol.embedding.EmbeddingResponse;
import com.ke.bella.openapi.protocol.tts.TtsPriceInfo;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.MatchUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Arrays;

@Slf4j
public class CostCalculator  {

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
            LOGGER.warn("no calculator implemented for " + endpoint);
            return true;
        }
        return calculator.checkPriceInfo(priceInfo);
    }

    @AllArgsConstructor
    @Getter
    enum CostCalculators {
        COMPLETION("/v*/chat/completions", completion),
        EMBEDDING("/v*/embeddings", embedding),
        TTS("/v*/audio/speech", tts),
        ASR_FLASH("/v*/audio/asr/flash", asr_flash),
        ASR_STREAM("/v*/audio/asr/stream", realtime),
        REAL_TIME("/v*/audio/realtime", realtime),
        ASR_TRANSCRIPTIONS("/v*/audio/transcriptions", asr_transcriptions)
        ;
        final String endpoint;
        final EndpointCostCalculator calculator;
    }

    static EndpointCostCalculator completion = new EndpointCostCalculator() {
        @Override
        public BigDecimal calculate(String priceInfo, Object usage) {
            CompletionPriceInfo price = JacksonUtils.deserialize(priceInfo, CompletionPriceInfo.class);
            CompletionResponse.TokenUsage tokenUsage = (CompletionResponse.TokenUsage) usage;
            return price.getInput().multiply(BigDecimal.valueOf(tokenUsage.getPrompt_tokens() / 1000.0))
                    .add(price.getOutput().multiply(BigDecimal.valueOf(tokenUsage.getCompletion_tokens() / 1000.0)));
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

    interface EndpointCostCalculator {
        BigDecimal calculate(String priceInfo, Object usage);
        boolean checkPriceInfo(String priceInfo);
    }
}

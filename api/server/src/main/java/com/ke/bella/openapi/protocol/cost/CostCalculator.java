package com.ke.bella.openapi.protocol.cost;

import com.ke.bella.openapi.protocol.asr.flash.FlashAsrPriceInfo;
import com.ke.bella.openapi.protocol.asr.transcription.TranscriptionsAsrPriceInfo;
import com.ke.bella.openapi.protocol.completion.CompletionPriceInfo;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.embedding.EmbeddingPriceInfo;
import com.ke.bella.openapi.protocol.embedding.EmbeddingResponse;
import com.ke.bella.openapi.protocol.images.ImagesEditsPriceInfo;
import com.ke.bella.openapi.protocol.images.ImagesPriceInfo;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.protocol.realtime.RealTimePriceInfo;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingLogHandler;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingPriceInfo;
import com.ke.bella.openapi.protocol.tts.TtsPriceInfo;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.MatchUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

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
        IMAGES("/v*/images/generations", images),
        IMAGES_EDITS("/v*/images/edits", images_edits),
        IMAGES_VARIATIONS("/v*/images/variations", images),
        SPEAKER_EMBEDDING("/v*/audio/speaker/embedding", speakerEmbedding)
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

                if (details.getImageTokenPrice() != null || details.getTextTokenPrice() != null) {
                    if (imageUsage.getInput_tokens_details() != null) {
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

			if (price != null) {

				int editCount = imageEditsUsage.getNum();
				if (price.getPricePerEdit() != null) {
					editCost = editCost.add(price.getPricePerEdit().multiply(BigDecimal.valueOf(editCount)));
				}


				if (price.getImageTokenPrice() != null && imageEditsUsage.getTotal_tokens() != null) {
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
            if (priceConfig.getPrice() == null) {
                return BigDecimal.ZERO;
            }
            
            // usage可能是Double类型(duration秒数)或者SpeakerEmbeddingUsage类型
            double durationSeconds = 0.0;
            if (usage instanceof Double) {
                durationSeconds = (Double) usage;
            } else if (usage instanceof SpeakerEmbeddingLogHandler.SpeakerEmbeddingUsage) {
                SpeakerEmbeddingLogHandler.SpeakerEmbeddingUsage speakerUsage = 
                    (SpeakerEmbeddingLogHandler.SpeakerEmbeddingUsage) usage;
                durationSeconds = speakerUsage.getDurationSeconds();
            } else if (usage != null) {
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

    interface EndpointCostCalculator {
        BigDecimal calculate(String priceInfo, Object usage);
        boolean checkPriceInfo(String priceInfo);
    }
}

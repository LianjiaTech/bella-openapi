package com.ke.bella.openapi.protocol.cost;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.protocol.completion.CompletionPriceInfo;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public class CompletionsCalHelper {

    public final static List<CompletionsCalElement> INPUT = Lists.newArrayList(
            CompletionsCalElement.IMAGE_INPUT,
            CompletionsCalElement.CACHE_READ,
            CompletionsCalElement.CACHE_CREATION);

    public final static List<CompletionsCalElement> OUTPUT = Lists.newArrayList(
            CompletionsCalElement.IMAGE_OUTPUT);

    @Getter
    @AllArgsConstructor
    public enum CompletionsCalElement {
        CACHE_READ(CompletionPriceInfo.RangePrice::getCachedRead, CompletionResponse.TokensDetail::getCached_tokens, "cached_tokens"),
        CACHE_CREATION(CompletionPriceInfo.RangePrice::getCachedCreation, CompletionResponse.TokensDetail::getCache_creation_tokens, "cache_creation_tokens"),
        IMAGE_INPUT(CompletionPriceInfo.RangePrice::getImageInput, CompletionResponse.TokensDetail::getImage_tokens, "image_tokens"),
        IMAGE_OUTPUT(CompletionPriceInfo.RangePrice::getImageOutput, CompletionResponse.TokensDetail::getImage_tokens, "image_tokens"),
        ;

        final Function<CompletionPriceInfo.RangePrice, BigDecimal> priceGetter;
        final Function<CompletionResponse.TokensDetail, Integer> tokensGetter;
        final String typeName;
    }

    public static Pair<BigDecimal, Integer> calculateAllElements(List<CompletionsCalElement> elements, CompletionPriceInfo.RangePrice rangePrice,
            CompletionResponse.TokensDetail tokensDetail, List<CostDetails.CostDetailItem> details) {
        if(tokensDetail == null) {
            return Pair.of(BigDecimal.ZERO, 0);
        }
        int totalTokens = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (CompletionsCalElement element : elements) {
            BigDecimal price = element.getPriceGetter().apply(rangePrice);
            Integer tokens = element.getTokensGetter().apply(tokensDetail);
            if(price != null && tokens != null && tokens > 0) {
                totalTokens += tokens;
                BigDecimal cost = price.multiply(BigDecimal.valueOf(tokens / 1000.0));
                amount = amount.add(cost);
                if(details != null) {
                    details.add(CostDetails.CostDetailItem.builder()
                            .type(element.getTypeName()).tokens(tokens)
                            .unitPrice(price).cost(cost).build());
                }
            }
        }
        return Pair.of(amount, totalTokens);
    }

}

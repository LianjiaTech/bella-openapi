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

    public final static List<CompletionsCalElement> INPUT = Lists.newArrayList(CompletionsCalElement.IMAGE_INPUT, CompletionsCalElement.CACHE_READ,
            CompletionsCalElement.CACHE_CREATION);
    public final static List<CompletionsCalElement> OUTPUT = Lists.newArrayList(CompletionsCalElement.IMAGE_OUTPUT);

    @Getter
    @AllArgsConstructor
    public enum CompletionsCalElement {
        CACHE_READ(CompletionPriceInfo::getCachedRead, CompletionResponse.TokensDetail::getCached_tokens),
        CACHE_CREATION(CompletionPriceInfo::getCachedCreation, CompletionResponse.TokensDetail::getCache_creation_tokens),
        IMAGE_INPUT(CompletionPriceInfo::getImageInput, CompletionResponse.TokensDetail::getImage_tokens),
        IMAGE_OUTPUT(CompletionPriceInfo::getImageOutput, CompletionResponse.TokensDetail::getImage_tokens),
        ;

        final Function<CompletionPriceInfo, BigDecimal> priceGetter;
        final Function<CompletionResponse.TokensDetail, Integer> tokensGetter;
    }

    public static Pair<BigDecimal, Integer> calculateAllElements(List<CompletionsCalElement> elements, CompletionPriceInfo priceInfo,
            CompletionResponse.TokensDetail tokensDetail) {
        if(tokensDetail == null) {
            return Pair.of(BigDecimal.ZERO, 0);
        }
        int totalTokens = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (CompletionsCalElement element : elements) {
            BigDecimal price = element.getPriceGetter().apply(priceInfo);
            Integer tokens = element.getTokensGetter().apply(tokensDetail);
            if(price != null && tokens != null) {
                totalTokens += tokens;
                amount = amount.add(price.multiply(BigDecimal.valueOf(tokens / 1000.0)));
            }
        }
        return Pair.of(amount, totalTokens);
    }

}

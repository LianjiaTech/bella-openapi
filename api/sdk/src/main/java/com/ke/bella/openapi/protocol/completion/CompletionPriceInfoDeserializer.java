package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class CompletionPriceInfoDeserializer extends JsonDeserializer<CompletionPriceInfo> {

    @Override
    public CompletionPriceInfo deserialize(JsonParser p, DeserializationContext context) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        CompletionPriceInfo priceInfo = new CompletionPriceInfo();

        if(node.has("unit")) {
            priceInfo.setUnit(node.get("unit").asText());
        }
        if(node.has("batchDiscount")) {
            priceInfo.setBatchDiscount(node.get("batchDiscount").asDouble());
        }

        if(node.has("tiers")) {
            priceInfo.setTiers(mapper.convertValue(
                    node.get("tiers"),
                    mapper.getTypeFactory().constructCollectionType(List.class, CompletionPriceInfo.Tier.class)));
        } else if(node.has("input") && node.has("output")) {
            CompletionPriceInfo.RangePrice rangePrice = new CompletionPriceInfo.RangePrice();
            rangePrice.setMinToken(0);
            rangePrice.setMaxToken(Integer.MAX_VALUE);
            rangePrice.setInput(getBigDecimal(node, "input"));
            rangePrice.setOutput(getBigDecimal(node, "output"));
            rangePrice.setImageInput(getBigDecimal(node, "imageInput"));
            rangePrice.setImageOutput(getBigDecimal(node, "imageOutput"));
            rangePrice.setCachedRead(getBigDecimal(node, "cachedRead"));
            rangePrice.setCachedCreation(getBigDecimal(node, "cachedCreation"));
            CompletionPriceInfo.Tier tier = new CompletionPriceInfo.Tier();
            tier.setInputRangePrice(rangePrice);
            tier.setOutputRangePrices(null);
            priceInfo.setTiers(Collections.singletonList(tier));
        }

        return priceInfo;
    }

    private BigDecimal getBigDecimal(JsonNode node, String fieldName) {
        if(node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).decimalValue();
        }
        return null;
    }
}

package com.ke.bella.openapi.protocol.alpha.search;

import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AlphaSearchPriceInfo implements IPriceInfo, Serializable {
    private BigDecimal pricePerRequest;
    private double supplierDiscount = 1.0;

    @Override
    public String getUnit() { return "分/请求"; }

    @Override
    public Map<String, String> description() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("pricePerRequest", "每次请求价格（分/请求）");
        result.put("supplierDiscount", "供应商折扣");
        return result;
    }

    public boolean validate() { return pricePerRequest != null && pricePerRequest.signum() >= 0; }
}

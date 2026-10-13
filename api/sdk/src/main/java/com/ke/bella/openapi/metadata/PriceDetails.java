package com.ke.bella.openapi.metadata;

import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class PriceDetails implements Serializable {
    private static final long serialVersionUID = 1L;
    private IPriceInfo priceInfo;
    private Map<String, String> displayPrice;
    private String unit;
}

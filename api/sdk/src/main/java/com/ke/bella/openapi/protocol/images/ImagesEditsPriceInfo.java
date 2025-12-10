package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ImagesEditsPriceInfo implements IPriceInfo, Serializable {
	private BigDecimal pricePerEdit;
	private BigDecimal imageTokenPrice;
	private double batchDiscount = 1.0;

	@Override
	public String getUnit() {
		return "元/张";
	}

	@Override
	public Map<String, String> description() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("pricePerEdit", "单张图像价格");
		map.put("imageTokenPrice", "图片token价格（/千token）");
		return map;
	}

	@Override
	public String toString() {
		return "单张图像价格：" + pricePerEdit;
	}
}

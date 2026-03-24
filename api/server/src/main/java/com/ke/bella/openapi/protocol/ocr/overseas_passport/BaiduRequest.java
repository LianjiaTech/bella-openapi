package com.ke.bella.openapi.protocol.ocr.overseas_passport;

import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduBaseRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BaiduRequest extends BaiduBaseRequest {
}

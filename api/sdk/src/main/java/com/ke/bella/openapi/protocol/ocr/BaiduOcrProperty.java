package com.ke.bella.openapi.protocol.ocr;

import java.util.Map;

import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 百度OCR配置属性
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode(callSuper = true)
public class BaiduOcrProperty extends OcrProperty {

    // 百度AI平台认证信息
    @NotBlank(message = "百度OCR AppId不能为空")
    private String appId;           // App ID

    @NotBlank(message = "百度OCR ApiKey不能为空")
    private String apiKey;          // API Key

    @NotBlank(message = "百度OCR SecretKey不能为空")
    private String secretKey;       // Secret Key

    // 网络连接配置
    private Integer connectionTimeoutMillis = 2000;    // 连接超时(毫秒)
    private Integer socketTimeoutMillis = 60000;       // 读取超时(毫秒)

    // 代理配置(可选)
    private String proxyHost;       // 代理服务器地址
    private Integer proxyPort;      // 代理服务器端口

    // 功能开关配置
    private Boolean detectDirection = false;    // 是否检测图像朝向
    private Boolean detectRisk = false;         // 是否开启身份证风险检测
    private Boolean detectPhoto = false;        // 是否检测头像内容
    private Boolean detectCard = false;         // 是否检测身份证进行裁剪

    @Override
    public Map<String, String> description() {
        Map<String, String> map = super.description();
        map.put("appId", "百度OCR应用ID");
        map.put("apiKey", "百度OCR API密钥");
        map.put("secretKey", "百度OCR Secret密钥");
        map.put("connectionTimeoutMillis", "连接超时时间(毫秒)");
        map.put("socketTimeoutMillis", "读取超时时间(毫秒)");
        map.put("proxyHost", "代理服务器地址");
        map.put("proxyPort", "代理服务器端口");
        map.put("detectDirection", "是否检测图像朝向");
        map.put("detectRisk", "是否开启身份证风险检测");
        map.put("detectPhoto", "是否检测头像内容");
        map.put("detectCard", "是否检测身份证进行裁剪");
        return map;
    }
}

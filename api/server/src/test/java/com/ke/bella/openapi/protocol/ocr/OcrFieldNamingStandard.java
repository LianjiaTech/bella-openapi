package com.ke.bella.openapi.protocol.ocr;

import java.util.HashSet;
import java.util.Set;

/**
 * OCR证件字段命名规范
 * 定义所有允许使用的OCR字段名称（超集）
 */
public class OcrFieldNamingStandard {

    /**
     * 所有允许使用的字段名称（超集）
     * 所有OCR响应类的字段名必须在此集合中
     */
    private static final Set<String> ALLOWED_FIELD_NAMES = new HashSet<>();

    static {
        // 个人信息字段
        ALLOWED_FIELD_NAMES.add("name");                // 姓名
        ALLOWED_FIELD_NAMES.add("sex");                 // 性别
        ALLOWED_FIELD_NAMES.add("nationality");         // 民族
        ALLOWED_FIELD_NAMES.add("birth_date");          // 出生日期

        // 地址信息字段
        ALLOWED_FIELD_NAMES.add("address");             // 地址/住址

        // 证件信息字段
        ALLOWED_FIELD_NAMES.add("idcard_number");       // 证件号/身份证号/公民身份号码

        // 签发信息字段
        ALLOWED_FIELD_NAMES.add("issue_authority");     // 签发机关
        ALLOWED_FIELD_NAMES.add("valid_date_start");    // 有效期开始
        ALLOWED_FIELD_NAMES.add("valid_date_end");      // 有效期结束

        // 银行卡字段
        ALLOWED_FIELD_NAMES.add("card_number");         // 银行卡号
        ALLOWED_FIELD_NAMES.add("bank_name");           // 银行名称
        ALLOWED_FIELD_NAMES.add("card_type");           // 卡类型
        ALLOWED_FIELD_NAMES.add("valid_date");          // 有效期

        // 特殊字段
        ALLOWED_FIELD_NAMES.add("eep_number");          // 通行证号（港澳台居民居住证）
        ALLOWED_FIELD_NAMES.add("issue_times");         // 签发次数，港澳台居民居住证、台胞证、返乡证
        ALLOWED_FIELD_NAMES.add("permit_number");       // 证件号码（港澳台居民往来大陆/内地通行证）
        ALLOWED_FIELD_NAMES.add("name_en");             // 英文姓名
        ALLOWED_FIELD_NAMES.add("idcard_name");         // 身份证姓名（港澳台居民往来大陆/内地通行证）
        ALLOWED_FIELD_NAMES.add("mrz");                 // MRZ码

        // 营业执照字段
        ALLOWED_FIELD_NAMES.add("unified_social_credit_code"); // 统一社会信用代码
        ALLOWED_FIELD_NAMES.add("license_number");      // 证照编号
        ALLOWED_FIELD_NAMES.add("entity_type");         // 企业类型
        ALLOWED_FIELD_NAMES.add("legal_representative"); // 法定代表人
        ALLOWED_FIELD_NAMES.add("business_scope");      // 经营范围
        ALLOWED_FIELD_NAMES.add("registered_capital");  // 注册资本
        ALLOWED_FIELD_NAMES.add("paid_in_capital");     // 实收资本
        ALLOWED_FIELD_NAMES.add("establishment_date");  // 成立日期
        ALLOWED_FIELD_NAMES.add("business_term_start"); // 营业期限开始日期
        ALLOWED_FIELD_NAMES.add("business_term_end");   // 营业期限结束日期
        ALLOWED_FIELD_NAMES.add("issue_date");          // 颁发日期/核准日期
        ALLOWED_FIELD_NAMES.add("taxpayer_id");         // 税务登记号
        ALLOWED_FIELD_NAMES.add("composition_form");    // 组成形式

        // 通用OCR字段
        ALLOWED_FIELD_NAMES.add("words");               // 识别出的文字列表

    }

    /**
     * 检查字段名是否允许使用
     *
     * @param fieldName 字段名
     *
     * @return true 如果字段名在允许列表中
     */
    public static boolean isAllowedFieldName(String fieldName) {
        return ALLOWED_FIELD_NAMES.contains(fieldName);
    }

    /**
     * 获取所有允许的字段名
     *
     * @return 所有允许使用的字段名集合
     */
    public static Set<String> getAllAllowedFieldNames() {
        return new HashSet<>(ALLOWED_FIELD_NAMES);
    }

    /**
     * 添加新的允许字段名（用于扩展）
     *
     * @param fieldName 字段名
     */
    public static void addAllowedFieldName(String fieldName) {
        ALLOWED_FIELD_NAMES.add(fieldName);
    }
}

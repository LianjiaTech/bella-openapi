package com.ke.bella.openapi.protocol.asr;

/**
 * 腾讯ASR引擎模型类型枚举
 * 支持电话场景和非电话场景的多种引擎模型
 */
public enum TencentEngineModelType {

    // === 电话场景 ===
    /**
     * 中文电话通用
     */
    PHONE_8K_ZH("8k_zh", "中文电话通用"),

    /**
     * 英文电话通用
     */
    PHONE_8K_EN("8k_en", "英文电话通用"),

    /**
     * 中文电话场景专用大模型引擎【大模型版】
     */
    PHONE_8K_ZH_LARGE("8k_zh_large", "中文电话场景专用大模型引擎【大模型版】"),

    // === 非电话场景 - 大模型版 ===
    /**
     * 中英粤+7种方言大模型引擎【大模型版】
     */
    NON_PHONE_16K_ZH_EN("16k_zh_en", "中英粤+7种方言大模型引擎【大模型版】"),

    /**
     * 普方英大模型引擎【大模型版】
     */
    NON_PHONE_16K_ZH_LARGE("16k_zh_large", "普方英大模型引擎【大模型版】"),

    /**
     * 多语种大模型引擎【大模型版】
     */
    NON_PHONE_16K_MULTI_LANG("16k_multi_lang", "多语种大模型引擎【大模型版】"),

    // === 非电话场景 - 中文 ===
    /**
     * 中文通用
     */
    NON_PHONE_16K_ZH("16k_zh", "中文通用"),

    /**
     * 中文繁体
     */
    NON_PHONE_16K_ZH_TW("16k_zh-TW", "中文繁体"),

    /**
     * 中文教育
     */
    NON_PHONE_16K_ZH_EDU("16k_zh_edu", "中文教育"),

    /**
     * 中文医疗
     */
    NON_PHONE_16K_ZH_MEDICAL("16k_zh_medical", "中文医疗"),

    /**
     * 中文法庭
     */
    NON_PHONE_16K_ZH_COURT("16k_zh_court", "中文法庭"),

    /**
     * 粤语
     */
    NON_PHONE_16K_YUE("16k_yue", "粤语"),

    // === 非电话场景 - 英文 ===
    /**
     * 英文通用
     */
    NON_PHONE_16K_EN("16k_en", "英文通用"),

    /**
     * 英文游戏
     */
    NON_PHONE_16K_EN_GAME("16k_en_game", "英文游戏"),

    /**
     * 英文教育
     */
    NON_PHONE_16K_EN_EDU("16k_en_edu", "英文教育"),

    // === 非电话场景 - 其他语种 ===
    /**
     * 韩语
     */
    NON_PHONE_16K_KO("16k_ko", "韩语"),

    /**
     * 日语
     */
    NON_PHONE_16K_JA("16k_ja", "日语"),

    /**
     * 泰语
     */
    NON_PHONE_16K_TH("16k_th", "泰语"),

    /**
     * 印度尼西亚语
     */
    NON_PHONE_16K_ID("16k_id", "印度尼西亚语"),

    /**
     * 越南语
     */
    NON_PHONE_16K_VI("16k_vi", "越南语"),

    /**
     * 马来语
     */
    NON_PHONE_16K_MS("16k_ms", "马来语"),

    /**
     * 菲律宾语
     */
    NON_PHONE_16K_FIL("16k_fil", "菲律宾语"),

    /**
     * 葡萄牙语
     */
    NON_PHONE_16K_PT("16k_pt", "葡萄牙语"),

    /**
     * 土耳其语
     */
    NON_PHONE_16K_TR("16k_tr", "土耳其语"),

    /**
     * 阿拉伯语
     */
    NON_PHONE_16K_AR("16k_ar", "阿拉伯语"),

    /**
     * 西班牙语
     */
    NON_PHONE_16K_ES("16k_es", "西班牙语"),

    /**
     * 印地语
     */
    NON_PHONE_16K_HI("16k_hi", "印地语"),

    /**
     * 法语
     */
    NON_PHONE_16K_FR("16k_fr", "法语"),

    /**
     * 德语
     */
    NON_PHONE_16K_DE("16k_de", "德语");

    private final String value;
    private final String description;

    TencentEngineModelType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据字符串值获取枚举
     * @param value 引擎模型类型字符串
     * @return 对应的枚举值，如果不存在则返回null
     */
    public static TencentEngineModelType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TencentEngineModelType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 验证引擎模型类型是否有效
     * @param value 引擎模型类型字符串
     * @return 是否有效
     */
    public static boolean isValid(String value) {
        return fromValue(value) != null;
    }

    /**
     * 获取所有有效的引擎模型类型值
     * @return 所有有效的引擎模型类型值数组
     */
    public static String[] getAllValues() {
        TencentEngineModelType[] types = values();
        String[] values = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            values[i] = types[i].value;
        }
        return values;
    }

    /**
     * 获取电话场景的引擎模型类型
     * @return 电话场景的引擎模型类型数组
     */
    public static String[] getPhoneSceneValues() {
        return new String[]{
            PHONE_8K_ZH.getValue(),
            PHONE_8K_EN.getValue(),
            PHONE_8K_ZH_LARGE.getValue()
        };
    }

    /**
     * 获取非电话场景的引擎模型类型
     * @return 非电话场景的引擎模型类型数组
     */
    public static String[] getNonPhoneSceneValues() {
        return new String[]{
            NON_PHONE_16K_ZH_EN.getValue(),
            NON_PHONE_16K_ZH_LARGE.getValue(),
            NON_PHONE_16K_MULTI_LANG.getValue(),
            NON_PHONE_16K_ZH.getValue(),
            NON_PHONE_16K_ZH_TW.getValue(),
            NON_PHONE_16K_ZH_EDU.getValue(),
            NON_PHONE_16K_ZH_MEDICAL.getValue(),
            NON_PHONE_16K_ZH_COURT.getValue(),
            NON_PHONE_16K_YUE.getValue(),
            NON_PHONE_16K_EN.getValue(),
            NON_PHONE_16K_EN_GAME.getValue(),
            NON_PHONE_16K_EN_EDU.getValue(),
            NON_PHONE_16K_KO.getValue(),
            NON_PHONE_16K_JA.getValue(),
            NON_PHONE_16K_TH.getValue(),
            NON_PHONE_16K_ID.getValue(),
            NON_PHONE_16K_VI.getValue(),
            NON_PHONE_16K_MS.getValue(),
            NON_PHONE_16K_FIL.getValue(),
            NON_PHONE_16K_PT.getValue(),
            NON_PHONE_16K_TR.getValue(),
            NON_PHONE_16K_AR.getValue(),
            NON_PHONE_16K_ES.getValue(),
            NON_PHONE_16K_HI.getValue(),
            NON_PHONE_16K_FR.getValue(),
            NON_PHONE_16K_DE.getValue()
        };
    }

    /**
     * 获取大模型版本的引擎模型类型
     * @return 大模型版本的引擎模型类型数组
     */
    public static String[] getLargeModelValues() {
        return new String[]{
            PHONE_8K_ZH_LARGE.getValue(),
            NON_PHONE_16K_ZH_EN.getValue(),
            NON_PHONE_16K_ZH_LARGE.getValue(),
            NON_PHONE_16K_MULTI_LANG.getValue()
        };
    }

    @Override
    public String toString() {
        return value;
    }
}

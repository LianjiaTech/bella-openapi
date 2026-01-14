package com.ke.bella.openapi.console;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.ke.bella.openapi.metadata.MetaDataOps;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.QueueMode;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ke.bella.openapi.common.EntityConstants.AUTHORIZER_TYPES;
import static com.ke.bella.openapi.common.EntityConstants.CHANNEL_PRIORITY;
import static com.ke.bella.openapi.common.EntityConstants.DATA_DESTINATIONS;
import static com.ke.bella.openapi.common.EntityConstants.ENDPOINT;
import static com.ke.bella.openapi.common.EntityConstants.MODEL;
import static com.ke.bella.openapi.common.EntityConstants.ModelJsonKey;
import static com.ke.bella.openapi.common.EntityConstants.OWNER_TYPES;
import static com.ke.bella.openapi.common.EntityConstants.PRIVATE;
import static com.ke.bella.openapi.common.EntityConstants.PUBLIC;
import static com.ke.bella.openapi.common.EntityConstants.SystemBasicCategory;
import static com.ke.bella.openapi.common.EntityConstants.SystemBasicEndpoint;
import static com.ke.bella.openapi.utils.MatchUtils.isAllText;
import static com.ke.bella.openapi.utils.MatchUtils.isBracesWithSpaces;
import static com.ke.bella.openapi.utils.MatchUtils.isTextStart;
import static com.ke.bella.openapi.utils.MatchUtils.isValidURL;

public class MetadataValidator {
    private static final LoadingCache<String, Pattern> baseEndpointPatternCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<String, Pattern>() {
                @Override
                public Pattern load(String key) {
                    String regex = StringUtils.replace(key, "*", "\\d+");
                    return Pattern.compile(regex);
                }
            });

    public static void checkEndpointOp(MetaDataOps.EndpointOp op, boolean create) {
        Assert.hasText(op.getEndpoint(), "能力点path不可为空");
        boolean systemEndpoint = Arrays.stream(SystemBasicEndpoint.values())
                .map(SystemBasicEndpoint::getEndpoint)
                .anyMatch(path -> matchPath(path, op.getEndpoint()));
        if(create) {
            Assert.isTrue(!systemEndpoint, "不可创建系统endpoint");
            Assert.isTrue(op.getEndpoint().matches(".*/v\\d+/.*"), "path格式必须为*/v{d}/*");
            Assert.isTrue(!op.getEndpoint().contains("_"), "能力点path不能包含下划线");
            Assert.isTrue(op.getEndpoint().length() <= 60, "能力点path长度不能超过60");
            Assert.hasText(op.getEndpointName(), "能力点名字不可为空");
            Assert.hasText(op.getMaintainerCode(), "维护人系统号不可为空");
            Assert.hasText(op.getMaintainerName(), "维护人名字不可为空");
        } else {
            if(systemEndpoint) {
                op.setEndpointName(null);
            }
            Assert.isTrue(StringUtils.isNotBlank(op.getEndpointName())
                    || StringUtils.isNotBlank(op.getMaintainerCode())
                    || StringUtils.isNotBlank(op.getMaintainerName())
                    || StringUtils.isNotBlank(op.getDocumentUrl()), "可修改字段全部为空，无法修改");
        }
        if(StringUtils.isNotEmpty(op.getEndpointName())) {
            Assert.isTrue(isTextStart(op.getEndpointName()), "能力点名称不可以数字/特殊字符/空格开头");
            Assert.isTrue(op.getEndpointName().length() <= 10, "能力点名称长度不能超过10");
        }
        if(StringUtils.isNotEmpty(op.getMaintainerCode())) {
            Assert.isTrue(StringUtils.isNumeric(op.getMaintainerCode()), "系统号必须为数字");
        }
        if(StringUtils.isNotEmpty(op.getMaintainerName())) {
            Assert.isTrue(isAllText(op.getMaintainerName()), "姓名不可以包含数字/特殊字符/空格开头");
        }
        if(StringUtils.isNotEmpty(op.getDocumentUrl())) {
            Assert.isTrue(isValidURL(op.getDocumentUrl()), "文档地址必须是有效的URL");
            Assert.isTrue(op.getDocumentUrl().length() <= 255, "文档地址长度不能超过255");
        }
    }

    public static void checkEndpointStatusOp(MetaDataOps.EndpointStatusOp op) {
        Assert.isTrue(Arrays.stream(SystemBasicEndpoint.values()).map(SystemBasicEndpoint::getEndpoint)
                .noneMatch(path -> matchPath(path, op.getEndpoint())), "不可修改系统endpoint");
        Assert.hasText(op.getEndpoint(), "能力点path不可为空");
    }

    public static void checkModelOp(MetaDataOps.ModelOp op, boolean create) {
        Assert.hasText(op.getModelName(), "模型名称不可为空");
        if(create) {
            Assert.isTrue(isTextStart(op.getModelName()), "模型名不可以数字/特殊字符/空格开头");
            Assert.notEmpty(op.getEndpoints(), "能力点不可为空");
            Assert.isTrue(op.getModelName().length() <= 60, "模型名长度不能超过60");
            Assert.hasText(op.getProperties(), "模型属性不能为空");
            Assert.hasText(op.getFeatures(), "模型特性不能为空");
            Assert.hasText(op.getOwnerType(), "模型所有者类型不能为空");
            Assert.hasText(op.getOwnerCode(), "模型所有者code不能为空");
            Assert.hasText(op.getOwnerName(), "模型所有者姓名不能为空");
        } else {
            Assert.isTrue(CollectionUtils.isNotEmpty(op.getEndpoints())
                    || StringUtils.isNotBlank(op.getDocumentUrl())
                    || StringUtils.isNotBlank(op.getProperties())
                    || StringUtils.isNotBlank(op.getFeatures()), "可修改字段全部为空，无法修改");
        }
        if(StringUtils.isNotEmpty(op.getOwnerType())) {
            Assert.isTrue(OWNER_TYPES.contains(op.getOwnerType()), "错误的所有者类型");
        }
    }

    public static void checkModelNameOp(MetaDataOps.ModelNameOp op) {
        Assert.hasText(op.getModelName(), "模型名不可为空");
    }

    public static void checkModelLinkOp(MetaDataOps.ModelLinkOp op) {
        Assert.hasText(op.getModelName(), "模型名不可为空");
        Assert.notNull(op.getLinkedTo(), "模型软链不可为null");
        Assert.isTrue(!op.getLinkedTo().equals(op.getModelName()), "循环软链");
    }

    public static void checkModelAuthorizerOp(MetaDataOps.ModelAuthorizerOp op) {
        Assert.hasText(op.getModel(), "模型名称不可为空");
        if(CollectionUtils.isNotEmpty(op.getAuthorizers())) {
            Assert.isTrue(op.getAuthorizers().stream().allMatch(x -> AUTHORIZER_TYPES.contains(x.getAuthorizerType())),
                    "错误的授权者类型");
            Assert.isTrue(op.getAuthorizers().stream().map(MetaDataOps.ModelAuthorizer::getAuthorizerCode).noneMatch(String::isEmpty),
                    "授权者code不能为空");
            Assert.isTrue(op.getAuthorizers().stream().map(MetaDataOps.ModelAuthorizer::getAuthorizerName).noneMatch(String::isEmpty),
                    "授权者名称不能为空");
        }
    }

    public static void checkChannelCreateOp(MetaDataOps.ChannelCreateOp op) {
        Assert.hasText(op.getEntityType(), "实体类型不可为空");
        Assert.hasText(op.getEntityCode(), "实体编码不可为空");
        if(op.getEntityType().equals(MODEL)) {
            Assert.hasText(op.getChannelInfo(), "模型通道的通道信息不可为空");
            Assert.hasText(op.getPriceInfo(), "模型通道的单价信息不可为空");
        }
        Assert.isTrue(op.getEntityType().equals(ENDPOINT) || op.getEntityType().equals(MODEL),
                "实体类型只能是endpoint或model");
        Assert.hasText(op.getProtocol(), "请求协议不可为空");
        Assert.hasText(op.getSupplier(), "供应商不可为空");
        Assert.hasText(op.getUrl(), "url不可为空");
        Assert.isTrue(DATA_DESTINATIONS.contains(op.getDataDestination()),
                "通道的数据流向只能是：" + String.join("或", DATA_DESTINATIONS));
        Assert.isTrue(CHANNEL_PRIORITY.contains(op.getPriority()),
                "通道的优先级只能是：" + String.join("或", CHANNEL_PRIORITY));
        Assert.isTrue(op.getTrialEnabled() == 1 || op.getTrialEnabled() == 0, "试用开关只能是0或1");
        Assert.hasText(op.getProtocol(), "请求协议不可为空字符串");
        Assert.hasText(op.getSupplier(), "供应商不可为空字符串");

        // Validate visibility if provided
        if(StringUtils.isNotEmpty(op.getVisibility())) {
            Assert.isTrue(PRIVATE.equals(op.getVisibility()) ||
                    PUBLIC.equals(op.getVisibility()),
                    "通道的可见性只能是：" + PRIVATE + "或" + PUBLIC);

            // If it's a private channel, validate owner information
            if(PRIVATE.equals(op.getVisibility())) {
                Assert.hasText(op.getOwnerType(), "私有通道的所有者类型不可为空");
                Assert.hasText(op.getOwnerCode(), "私有通道的所有者编码不可为空");
                Assert.isTrue(OWNER_TYPES.contains(op.getOwnerType()),
                        "所有者类型只能是：" + String.join("或", OWNER_TYPES));
            }
        }

        checkJsonInfo(op.getChannelInfo());
        checkJsonInfo(op.getPriceInfo());
    }

    public static void checkChannelUpdateOp(MetaDataOps.ChannelUpdateOp op) {
        Assert.hasText(op.getChannelCode(), "通道编码不可为空");
        Assert.isTrue(StringUtils.isNotBlank(op.getPriority())
                || StringUtils.isNotBlank(op.getChannelInfo())
                || StringUtils.isNotBlank(op.getPriceInfo())
                || StringUtils.isNotBlank(op.getQueueName())
                || op.getQueueMode() != null, "可修改字段全部为空，无法修改");
        if(op.getPriority() != null) {
            Assert.isTrue(CHANNEL_PRIORITY.contains(op.getPriority()),
                    "通道的优先级只能是：" + String.join("或", CHANNEL_PRIORITY));
        }
        Assert.isTrue(op.getTrialEnabled() == null || op.getTrialEnabled() == 1 || op.getTrialEnabled() == 0, "试用开关只能是0或1");
        checkJsonInfo(op.getChannelInfo());
        checkJsonInfo(op.getPriceInfo());
    }

    private static void checkJsonInfo(String info) {
        // 只检查是否是json，其他信息在service中根据类型判断
        if(info != null) {
            Map<String, Object> map = json2Map(info);
            Assert.isTrue(map == null || !map.isEmpty(), "信息非json格式");
        }
    }

    public static void checkChannelStatusOp(MetaDataOps.ChannelStatusOp op) {
        Assert.hasText(op.getChannelCode(), "通道编码不可为空");
    }

    public static void checkCategoryCreateOp(MetaDataOps.CategoryCreateOp op) {
        if(op.getParentCode() != null) {
            Assert.hasText(op.getParentCode(), "父类目编码不可为空字符串");
        }
        Assert.isTrue(Arrays.stream(SystemBasicCategory.values())
                .noneMatch(x -> {
                    String systemParentCode = x.getParent() == null ? "" : x.getParent().getCode();
                    String parentCode = op.getParentCode() == null ? "" : op.getParentCode();
                    return parentCode.equals(systemParentCode) && op.getCategoryName().equals(x.getName());
                }),
                "不可创建系统类目");
        Assert.hasText(op.getCategoryName(), "类目名称不可为空");
        Assert.isTrue(op.getCategoryName().length() <= 10, "类目名称长度不可超过10");
        Assert.isTrue(isTextStart(op.getCategoryName()), "类目名称必须以文字开头");
    }

    public static void checkCategoryStatus(MetaDataOps.CategoryStatusOp op) {
        Assert.hasText(op.getCategoryCode(), "类目编码不可为空");
    }

    public static void checkEndpointCategoryOp(MetaDataOps.EndpointCategoriesOp op) {
        Assert.hasText(op.getEndpoint(), "能力点path不可为空");
        Assert.notEmpty(op.getCategoryCodes(), "类目编码不可为空");
        Assert.isTrue(Arrays.stream(SystemBasicEndpoint.values())
                .noneMatch(endpoint -> matchPath(endpoint.getEndpoint(), op.getEndpoint())
                        && op.getCategoryCodes().contains(endpoint.getCategory().getCode())),
                "不可修改系统默认的能力点类目");
    }

    public static void checkReplaceEndpointCategoryOp(MetaDataOps.EndpointCategoriesOp op) {
        Assert.hasText(op.getEndpoint(), "能力点path不可为空");
        if(CollectionUtils.isNotEmpty(op.getCategoryCodes())) {
            op.getCategoryCodes().forEach(code -> Assert.hasText(code, "类目编码不可为空"));
        }
    }

    /**
     * 检查路径是否匹配
     *
     * @param match 要匹配的路径
     * @param path  要检查的理解
     *
     * @return 匹配返回true，不匹配返回false
     */
    public static boolean matchPath(String match, String path) {
        try {
            Matcher matcher = baseEndpointPatternCache.get(match).matcher(path);
            return matcher.matches();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> json2Map(String jsonStr) {
        if(isBracesWithSpaces(jsonStr)) {
            return null;
        }
        return JacksonUtils.toMap(jsonStr);
    }

    /**
     * 检查json是否符合要求
     *
     * @param map      jsonMap
     * @param endpoint 能力点，对应 {@link ModelJsonKey} 中的endpoint
     * @param field    model实体的字段名，对应 {@link ModelJsonKey} 中的field
     *
     * @return 如果存在不符合要求的key，返回不合法的提示，否则返回null
     */
    public static String generateInvalidModelJsonKeyMessage(Map<String, Object> map, String endpoint, String field) {
        List<ModelJsonKey> keys = Arrays.stream(ModelJsonKey.values())
                .filter(key -> key.getFied().equals(field) && matchPath(key.getEndpoint(), endpoint))
                .collect(Collectors.toList());
        if(CollectionUtils.isEmpty(keys)) {
            return null;
        }
        List<ModelJsonKey> invalidKeys = keys.stream()
                .filter(key -> map.containsKey(key.getCode()) && !map.get(key.getCode()).getClass().equals(key.getType()))
                .collect(Collectors.toList());
        if(CollectionUtils.isNotEmpty(invalidKeys)) {
            StringBuilder sb = new StringBuilder(field).append("中以下内容不符合规范：\r\n");
            invalidKeys.forEach(key -> sb.append(key.getCode())
                    .append("的值类型为：")
                    .append(key.getType().getSimpleName())
                    .append(", "));
            sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        }
        return null;
    }
}

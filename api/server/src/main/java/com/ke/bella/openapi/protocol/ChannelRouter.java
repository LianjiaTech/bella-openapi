package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.queue.QueueMode;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ke.bella.openapi.common.EntityConstants.LOWEST_SAFETY_LEVEL;

@Component
public class ChannelRouter {
    @Autowired
    private ChannelService channelService;
    @Autowired
    private ModelService modelService;
    @Autowired
    private MetricsManager metricsManager;
    @Autowired
    private LimiterManager limiterManager;
    @Autowired
    private RedissonClient redisson;
    @Value("${bella.openapi.free.rpm:5}")
    private Integer freeRpm;
    @Value("${bella.openapi.free.concurrent:1}")
    private Integer freeConcurrent;
    @Value("${bella.openapi.route-affinity.ttl-seconds:300}")
    private Long routeAffinityTtlSeconds;

    public ChannelDB route(String endpoint, String model, ApikeyInfo apikeyInfo, boolean isMock) {
        return route(endpoint, model, apikeyInfo, isMock, false);
    }

    public ChannelDB route(String endpoint, String model, ApikeyInfo apikeyInfo, boolean isMock, String affinityKey) {
        return route(endpoint, model, apikeyInfo, isMock, false, affinityKey);
    }

    /**
     * Route to channel with optional direct mode
     *
     * @param isDirectMode if true, skips availability checks (no Redis queries)
     */
    public ChannelDB route(String endpoint, String model, ApikeyInfo apikeyInfo, boolean isMock, boolean isDirectMode) {
        return route(endpoint, model, apikeyInfo, isMock, isDirectMode, null);
    }

    /**
     * Route to channel with optional direct mode and cache affinity key.
     *
     * @param isDirectMode if true, skips availability checks (no Redis queries)
     * @param affinityKey stable Redis key used to keep long-prefix requests on the same channel
     */
    public ChannelDB route(String endpoint, String model, ApikeyInfo apikeyInfo, boolean isMock, boolean isDirectMode, String affinityKey) {
        List<ChannelDB> channels = routeCandidates(endpoint, model, apikeyInfo, isMock, isDirectMode, affinityKey);
        ChannelDB channel = channels.get(0);
        if(!isMock && StringUtils.isNotBlank(affinityKey)) {
            setCachedAffinityChannel(affinityKey, channel);
        }
        return channel;
    }

    public List<ChannelDB> routeCandidates(String endpoint, String model, ApikeyInfo apikeyInfo, boolean isMock, boolean isDirectMode,
            String affinityKey) {
        if(StringUtils.isBlank(endpoint) && StringUtils.isBlank(model)) {
            throw routeFailure(endpoint, model, new BizParamCheckException("没有可用渠道"));
        }
        List<ChannelDB> channels;
        String entityCode;
        if(StringUtils.isNotEmpty(model)) {
            String terminal = modelService.fetchTerminalModelName(model);
            entityCode = terminal;
            channels = channelService.listActives(EntityConstants.MODEL, terminal);
        } else {
            entityCode = endpoint;
            channels = channelService.listActives(EntityConstants.ENDPOINT, endpoint);
        }
        if(isMock) {
            ChannelDB origin = CollectionUtils.isEmpty(channels) ? null : orderCandidates(channels, affinityKey).get(0);
            return Collections.singletonList(mockChannel(origin));
        }
        if(CollectionUtils.isEmpty(channels)) {
            throw routeFailure(endpoint, model, new BizParamCheckException("没有可用渠道").withModel(model));
        }
        channels = filter(endpoint, channels, entityCode, apikeyInfo, isDirectMode);
        channels = orderCandidates(channels, affinityKey);
        if(CollectionUtils.isEmpty(channels)) {
            throw routeFailure(endpoint, entityCode, new BellaException.RateLimitException("渠道当前负载过高，请稍后重试"));
        }
        return channels;
    }

    public ChannelDB route(String channelCode) {
        ChannelDB channelDB = channelService.getOne(channelCode);
        if(channelDB == null) {
            throw new BizParamCheckException("channelCode不存在");
        }
        return channelDB;
    }

    /**
     * 1、筛选账户支持的数据流向（风控） 2、筛选可用的渠道
     *
     * @param channels
     * @param isDirectMode if true, skips availability checks (no Redis queries)
     *
     * @return
     */
    private List<ChannelDB> filter(String endpoint, List<ChannelDB> channels, String entityCode, ApikeyInfo apikeyInfo) {
        return filter(endpoint, channels, entityCode, apikeyInfo, false);
    }

    private List<ChannelDB> filter(String endpoint, List<ChannelDB> channels, String entityCode, ApikeyInfo apikeyInfo, boolean isDirectMode) {
        List<ChannelDB> endpointMatched = channels.stream()
                .filter(channel -> AdaptorManager.getInstance().support(endpoint, channel.getProtocol()))
                .collect(Collectors.toList());

        if(CollectionUtils.isEmpty(endpointMatched)) {
            throw routeFailure(endpoint, entityCode, new BizParamCheckException("没有支持当前endpoint的可用渠道: " + endpoint));
        }

        Byte safetyLevel = apikeyInfo.getSafetyLevel();
        String accountType = apikeyInfo.getOwnerType();
        String accountCode = apikeyInfo.getOwnerCode();
        List<ChannelDB> filtered = endpointMatched.stream()
                .filter(channel -> !EntityConstants.PRIVATE.equals(channel.getVisibility()) ||
                        (accountType.equals(channel.getOwnerType()) && accountCode.equals(channel.getOwnerCode())))
                .filter(channel -> getSafetyLevelLimit(channel.getDataDestination()) <= safetyLevel)
                .collect(Collectors.toList());
        if(CollectionUtils.isEmpty(filtered)) {
            if(LOWEST_SAFETY_LEVEL.equals(safetyLevel)) {
                filtered = endpointMatched.stream().filter(this::isTestUsed)
                        .collect(Collectors.toList());
            }
            if(CollectionUtils.isEmpty(filtered)) {
                throw new BellaException.AuthorizationException("未经安全合规审核，没有使用权限");
            }
            if(freeAkOverload(EndpointContext.getProcessData().getAkCode(), entityCode)) {
                throw new BellaException.RateLimitException("当前使用试用额度,每分钟最多请求" + freeRpm + "次, 且并行请求数不能高于" + freeConcurrent);
            }
        }

        // Direct mode: skip availability checks (no Redis queries)
        if(!isDirectMode) {
            Set<String> unavailableSet = metricsManager.getAllUnavailableChannels(
                    filtered.stream().map(ChannelDB::getChannelCode).collect(Collectors.toList()));
            filtered = filtered.stream()
                    .filter(channel -> channel.getDataDestination().equals(EntityConstants.PROTECTED) ||
                            channel.getDataDestination().equals(EntityConstants.INNER) ||
                            !unavailableSet.contains(channel.getChannelCode()))
                    .collect(Collectors.toList());
        }
        if(CollectionUtils.isEmpty(filtered)) {
            throw routeFailure(endpoint, entityCode, new BellaException.RateLimitException("渠道当前负载过高，请稍后重试"));
        }
        return filtered;
    }

    private boolean isTestUsed(ChannelDB channel) {
        return 1 == channel.getTrialEnabled() && !EntityConstants.PRIVATE.equals(channel.getVisibility());
    }

    private boolean freeAkOverload(String akCode, String entityCode) {
        return limiterManager.getRequestCountPerMinute(akCode, entityCode) >= freeRpm
                || limiterManager.getCurrentConcurrentCount(akCode, entityCode) >= freeConcurrent;
    }

    private Byte getSafetyLevelLimit(String dataDestination) {
        switch (dataDestination) {
        case EntityConstants.PROTECTED:
            return 10;
        case EntityConstants.INNER:
            return 20;
        case EntityConstants.MAINLAND:
            return 30;
        case EntityConstants.OVERSEAS:
            return 40;
        }
        return 40;
    }

    private List<ChannelDB> orderCandidates(List<ChannelDB> channels, String affinityKey) {
        String cachedChannelCode = StringUtils.isNotBlank(affinityKey) ? getCachedChannelCode(affinityKey) : null;
        List<ChannelDB> ordered = new ArrayList<>(channels);
        Collections.shuffle(ordered);
        ordered.sort(Comparator.comparingInt(this::routeRank)
                .thenComparing(channel -> !channel.getChannelCode().equals(cachedChannelCode))
                .thenComparing(channel -> !isQueueChannel(channel)));
        return ordered;
    }

    public static boolean isQueueChannel(ChannelDB channel) {
        return StringUtils.isNotBlank(channel.getQueueName()) && QueueMode.of(channel.getQueueMode()) != QueueMode.NONE;
    }

    private int routeRank(ChannelDB channel) {
        int visibilityOffset = EntityConstants.PRIVATE.equals(channel.getVisibility()) ? 0 : 3;
        switch (channel.getPriority()) {
        case EntityConstants.HIGH:
            return visibilityOffset;
        case EntityConstants.NORMAL:
            return visibilityOffset + 1;
        default:
            return visibilityOffset + 2;
        }
    }

    private String getCachedChannelCode(String affinityKey) {
        try {
            Object value = redisson.getBucket(affinityKey).get();
            return value == null ? null : value.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public void setCachedAffinityChannel(String affinityKey, ChannelDB channel) {
        try {
            RBucket<String> bucket = redisson.getBucket(affinityKey);
            bucket.set(channel.getChannelCode(), routeAffinityTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private ChannelDB mockChannel(ChannelDB origin) {
        ChannelDB channel = new ChannelDB();
        channel.setChannelCode("ch-mock");
        channel.setProtocol("MockAdaptor");
        channel.setEntityType(EntityConstants.ENDPOINT);
        channel.setEntityCode("mock");
        channel.setPriceInfo("{}");
        channel.setChannelInfo(origin != null ? origin.getChannelInfo() : "{}");
        channel.setSupplier("system");
        channel.setUrl("");
        return channel;
    }

    public ChannelDB route(String endpoint, String model, ApikeyInfo apikey, Integer queueMode) {
        List<ChannelDB> channels = listAvailableChannels(endpoint, model, apikey, queueMode);
        if(CollectionUtils.isEmpty(channels)) {
            throw routeFailure(endpoint, model, new BizParamCheckException("没有可用通道").withModel(model));
        }
        return channels.get(0);
    }

    public List<ChannelDB> listAvailableChannels(String endpoint, String model, ApikeyInfo apikey, Integer queueMode) {
        if(StringUtils.isBlank(endpoint)) {
            throw new BizParamCheckException("endpoint不能为空");
        }

        List<ChannelDB> channels;
        if(StringUtils.isBlank(model)) {
            channels = channelService.listActives(EntityConstants.ENDPOINT, endpoint);
        } else {
            String terminalName = modelService.fetchTerminalModelName(model);
            channels = channelService.listActives(EntityConstants.MODEL, terminalName);
        }

        if(CollectionUtils.isEmpty(channels)) {
            return Collections.emptyList();
        }

        return channels.stream()
                .filter(channel -> queueMode == null
                        || QueueMode.of(channel.getQueueMode()).supports(queueMode))
                .filter(channel -> isAccessible(channel, apikey))
                .filter(channel -> isSafetyCompliant(channel, apikey))
                .sorted((c1, c2) -> routeRank(c1) - routeRank(c2))
                .collect(Collectors.toList());
    }

    private boolean isAccessible(ChannelDB channel, ApikeyInfo apikeyInfo) {
        if(!EntityConstants.PRIVATE.equals(channel.getVisibility())) {
            return true;
        }
        return apikeyInfo.getOwnerType().equals(channel.getOwnerType())
                && apikeyInfo.getOwnerCode().equals(channel.getOwnerCode());
    }

    private boolean isSafetyCompliant(ChannelDB channel, ApikeyInfo apikeyInfo) {
        return getSafetyLevelLimit(channel.getDataDestination()) <= apikeyInfo.getSafetyLevel();
    }

    private <T extends RuntimeException> T routeFailure(String endpoint, String model, T exception) {
        EndpointContext.getProcessData().setFailureStage(EndpointProcessData.FAILURE_STAGE_ROUTE);
        return exception;
    }
}

package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ke.bella.openapi.common.EntityConstants.LOWEST_SAFETY_LEVEL;

@Component
public class ChannelRouter {
    private final Random random = new Random();
    @Autowired
    private ChannelService channelService;
    @Autowired
    private ModelService modelService;
    @Autowired
    private MetricsManager metricsManager;
    @Autowired
    private LimiterManager limiterManager;
    @Value("${bella.openapi.free.rpm:5}")
    private Integer freeRpm;
    @Value("${bella.openapi.free.concurrent:1}")
    private Integer freeConcurrent;

    public ChannelDB route(String endpoint, String model, ApikeyInfo apikeyInfo, boolean isMock) {
        if(StringUtils.isBlank(endpoint) && StringUtils.isBlank(model)) {
            throw new BizParamCheckException("没有可用渠道");
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
        if(CollectionUtils.isEmpty(channels)) {
            if(isMock) {
                return mockChannel(null);
            } else {
                throw new BizParamCheckException("没有可用渠道");
            }
        }
        if(!isMock) {
            channels = filter(channels, entityCode, apikeyInfo);
        }
        channels = pickMaxPriority(channels);
        ChannelDB channel = random(channels);
        return isMock ? mockChannel(channel) : channel;
    }

    /**
     * 1、筛选账户支持的数据流向（风控） 2、筛选可用的渠道
     *
     * @param channels
     *
     * @return
     */
    private List<ChannelDB> filter(List<ChannelDB> channels, String entityCode, ApikeyInfo apikeyInfo) {
        Byte safetyLevel = apikeyInfo.getSafetyLevel();
        String accountType = apikeyInfo.getOwnerType();
        String accountCode = apikeyInfo.getOwnerCode();
        List<ChannelDB> filtered = channels.stream()
                .filter(channel -> !EntityConstants.PRIVATE.equals(channel.getVisibility()) ||
                        (accountType.equals(channel.getOwnerType()) && accountCode.equals(channel.getOwnerCode())))
                .filter(channel -> getSafetyLevelLimit(channel.getDataDestination()) <= safetyLevel)
                .collect(Collectors.toList());
        if(CollectionUtils.isEmpty(filtered)) {
            if(LOWEST_SAFETY_LEVEL.equals(safetyLevel)) {
                filtered = channels.stream().filter(this::isTestUsed)
                        .collect(Collectors.toList());
            }
            if(CollectionUtils.isEmpty(filtered)) {
                throw new ChannelException.AuthorizationException("未经安全合规审核，没有使用权限");
            }
            if(freeAkOverload(EndpointContext.getProcessData().getAkCode(), entityCode)) {
                throw new ChannelException.RateLimitException("当前使用试用额度,每分钟最多请求" + freeRpm + "次, 且并行请求数不能高于" + freeConcurrent);
            }
        }
        Set<String> unavailableSet = metricsManager.getAllUnavailableChannels(
                filtered.stream().map(ChannelDB::getChannelCode).collect(Collectors.toList()));
        filtered = filtered.stream()
                .filter(channel -> channel.getDataDestination().equals(EntityConstants.PROTECTED) ||
                        channel.getDataDestination().equals(EntityConstants.INNER) ||
                        !unavailableSet.contains(channel.getChannelCode()))
                .collect(Collectors.toList());
        if(CollectionUtils.isEmpty(filtered)) {
            throw new ChannelException.RateLimitException("渠道当前负载过高，请稍后重试");
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

    private List<ChannelDB> pickMaxPriority(List<ChannelDB> channels) {
        List<ChannelDB> highest = new ArrayList<>();
        String curVisibility = EntityConstants.PUBLIC;
        String curPriority = EntityConstants.LOW;
        for (ChannelDB channel : channels) {
            String priority = channel.getPriority();
            String visibility = StringUtils.isNotBlank(channel.getVisibility()) ? channel.getVisibility() : EntityConstants.PUBLIC;
            int compare = compare(priority, curPriority, visibility, curVisibility);
            if(compare < 0) {
                continue;
            }
            if(compare > 0) {
                highest.clear();
                curPriority = priority;
                curVisibility = visibility;
            }
            highest.add(channel);
        }
        return highest;
    }

    private int compare(String priority, String curPriority, String visibility, String curVisibility) {
        if(visibility.equals(curVisibility)) {
            if(priority.equals(curPriority)) {
                return 0;
            }
            if(priority.equals(EntityConstants.LOW)) {
                return -1;
            }
            if(priority.equals(EntityConstants.NORMAL)) {
                if(curPriority.equals(EntityConstants.HIGH)) {
                    return -1;
                }
            }
            return 1;
        } else {
            return visibility.equals(EntityConstants.PRIVATE) ? 1 : -1;
        }
    }

    private ChannelDB random(List<ChannelDB> list) {
        if(list.size() == 1) {
            return list.get(0);
        }
        int rand = random.nextInt(list.size());
        return list.get(rand);
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

}

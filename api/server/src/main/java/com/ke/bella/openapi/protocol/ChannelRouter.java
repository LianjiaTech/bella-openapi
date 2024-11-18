package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ChannelRouter {
    private final Random random = new Random();
    @Autowired
    private ChannelService channelService;
    @Autowired
    private ModelService modelService;
    @Autowired
    private MetricsManager metricsManager;

    public ChannelDB route(String endpoint, String model, boolean isMock) {
        if(isMock) {
            return mockChannel();
        }
        List<ChannelDB> channels;
        if(model != null) {
            String terminal = modelService.fetchTerminalModelName(model);
            channels = channelService.listActives(EntityConstants.MODEL, terminal);
        } else {
            channels = channelService.listActives(EntityConstants.MODEL, endpoint);
        }
        Assert.isTrue(CollectionUtils.isNotEmpty(channels), "没有可用渠道");
        channels = filter(channels);
        Assert.notNull(channels, "没有可用渠道");
        channels = pickMaxPriority(channels);
        return random(channels);
    }

    /**
     * 1、筛选账户支持的数据流向（风控） 2、筛选可用的渠道
     *
     * @param channels
     *
     * @return
     */
    private List<ChannelDB> filter(List<ChannelDB> channels) {
        Byte safetyLevel = BellaContext.getApikey().getSafetyLevel();
        Set<String> unavailableSet = metricsManager.getAllUnavailableChannels(
                channels.stream().map(ChannelDB::getChannelCode).collect(Collectors.toList()));
        channels = channels.stream().filter(channel -> getSafetyLevelLimit(channel.getDataDestination()) <= safetyLevel)
                .filter(channel -> !unavailableSet.contains(channel.getChannelCode()))
                .collect(Collectors.toList());
        return channels;
    }

    private Byte getSafetyLevelLimit(String dataDestination) {
        switch (dataDestination) {
        case EntityConstants.INNER:
            return 0;
        case EntityConstants.MAINLAND:
            return 1;
        case EntityConstants.OVERSEAS:
            return 2;
        }
        return 2;
    }

    private List<ChannelDB> pickMaxPriority(List<ChannelDB> channels) {
        List<ChannelDB> highest = new ArrayList<>();
        String max = EntityConstants.LOW;
        for (ChannelDB channel : channels) {
            String priority = channel.getPriority();
            int compare = compare(priority, max);
            if(compare < 0) {
                continue;
            }
            if(compare > 0) {
                highest.clear();
                max = priority;
                highest.add(channel);
            }
        }
        return highest;
    }

    private int compare(String priority, String target) {
        if(priority.equals(target)) {
            return 0;
        }
        if(priority.equals(EntityConstants.LOW)) {
            return -1;
        }
        if(priority.equals(EntityConstants.NORMAL)) {
            if(target.equals(EntityConstants.HIGH)) {
                return -1;
            }
        }
        return 1;
    }

    private ChannelDB random(List<ChannelDB> list) {
        if(list.size() == 1) {
            return list.get(0);
        }
        int rand = random.nextInt(list.size());
        return list.get(rand);
    }

    private ChannelDB mockChannel() {
        ChannelDB channel = new ChannelDB();
        channel.setChannelCode("ch-mock");
        channel.setProtocol("MockAdaptor");
        channel.setEntityType(EntityConstants.ENDPOINT);
        channel.setEntityCode("mock");
        channel.setPriceInfo("{}");
        channel.setChannelInfo("{}");
        channel.setSupplier("AIT");
        channel.setUrl("");
        return channel;
    }

}

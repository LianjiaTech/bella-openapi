package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Direct mode channel router that skips availability checks and complex filtering.
 * Only checks visibility and data destination, no Redis queries for channel availability.
 */
@Component
public class DirectChannelRouter {
    private final Random random = new Random();

    @Autowired
    private ChannelService channelService;
    @Autowired
    private ModelService modelService;

    /**
     * Route to channel in direct mode. Simplified routing logic:
     * 1. Find channels by model
     * 2. Filter only by visibility and data destination
     * 3. No availability check (skip Redis queries)
     * 4. Random selection from filtered channels
     */
    public ChannelDB routeDirect(String model, ApikeyInfo apikeyInfo) {
        if (StringUtils.isBlank(model)) {
            throw new BizParamCheckException("Direct mode requires model specification");
        }

        // Get channels for the terminal model
        String terminal = modelService.fetchTerminalModelName(model);
        List<ChannelDB> channels = channelService.listActives(EntityConstants.MODEL, terminal);

        if (CollectionUtils.isEmpty(channels)) {
            throw new BizParamCheckException("No channels available for model: " + model);
        }

        // Simple filtering: only visibility and data destination
        channels = filterDirect(channels, apikeyInfo);

        if (CollectionUtils.isEmpty(channels)) {
            throw new BizParamCheckException("No accessible channels for model: " + model);
        }

        // Random selection
        return randomSelect(channels);
    }

    /**
     * Direct mode filtering - only checks visibility and data destination.
     * No Redis availability checks, no rate limit checks.
     */
    private List<ChannelDB> filterDirect(List<ChannelDB> channels, ApikeyInfo apikeyInfo) {
        Byte safetyLevel = apikeyInfo.getSafetyLevel();
        String accountType = apikeyInfo.getOwnerType();
        String accountCode = apikeyInfo.getOwnerCode();

        return channels.stream()
                // Filter by visibility: private channels only for owner
                .filter(channel -> !EntityConstants.PRIVATE.equals(channel.getVisibility()) ||
                        (accountType.equals(channel.getOwnerType()) && accountCode.equals(channel.getOwnerCode())))
                // Filter by safety level
                .filter(channel -> getSafetyLevelLimit(channel.getDataDestination()) <= safetyLevel)
                .collect(Collectors.toList());
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

    private ChannelDB randomSelect(List<ChannelDB> list) {
        if (list.size() == 1) {
            return list.get(0);
        }
        int rand = random.nextInt(list.size());
        return list.get(rand);
    }
}

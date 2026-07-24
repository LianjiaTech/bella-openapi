package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.limiter.QpsCheckResult;
import com.ke.bella.openapi.protocol.limiter.QpsLimiterManager;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.DateTimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class RealtimeTtsAdmissionService {
    @Autowired
    private QpsLimiterManager qpsLimiterManager;
    @Autowired
    private ApikeyService apikeyService;
    @Autowired
    private ChannelRouter router;
    @Autowired
    private LimiterManager limiterManager;

    public ChannelDB admit(String endpoint, String model, ApikeyInfo apikey, boolean mock) {
        QpsCheckResult qps = qpsLimiterManager.checkLimit(apikey.getCode(), apikey.getQpsLimit());
        if(!qps.isAllowed()) {
            throw new BellaException.RateLimitException(
                    String.format("QPS 超过限制（当前: %d, 限制: %d），请 1 秒后重试", qps.getCurrentQps(), qps.getLimit()));
        }
        checkMonthQuota(apikey);
        ChannelDB channel = router.route(endpoint, model, apikey, mock);
        EndpointContext.setEndpointData(channel);
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        return channel;
    }

    private void checkMonthQuota(ApikeyInfo apikey) {
        if(apikey.getParentInfo() == null || apikey.getMonthQuota().doubleValue() > 0) {
            BigDecimal cost = apikeyService.loadCost(apikey.getCode(), DateTimeUtils.getCurrentMonth());
            double costVal = cost.doubleValue() / 100.0;
            if(apikey.getMonthQuota().doubleValue() <= costVal) {
                throw new BellaException.RateLimitException("已达每月额度上限, limit:" + apikey.getMonthQuota() + ", cost:" + costVal);
            }
        }
        if(apikey.getParentInfo() != null) {
            BigDecimal quota = apikey.getParentInfo().getMonthQuota();
            BigDecimal cost = apikeyService.loadCost(apikey.getParentCode(), DateTimeUtils.getCurrentMonth());
            double costVal = cost.doubleValue() / 100.0;
            if(quota.doubleValue() <= costVal) {
                throw new BellaException.RateLimitException("主ak的总额度已达上限, limit:" + quota + ", cost:" + costVal);
            }
        }
    }
}

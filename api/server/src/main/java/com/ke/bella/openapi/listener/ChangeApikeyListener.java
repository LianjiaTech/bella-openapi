package com.ke.bella.openapi.listener;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.db.repo.ApikeyRepo;
import com.ke.bella.openapi.event.ApiKeyChangeEvent;
import com.ke.bella.openapi.service.ApikeyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class ChangeApikeyListener {

    @Autowired
    private ApikeyRepo apikeyRepo;

    @Autowired
    private ApikeyService apikeyService;

    @Async("cacheTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleApiKeyChange(ApiKeyChangeEvent event) {
        if(event == null || CollectionUtils.isEmpty(event.getAkCodes())) {
            return;
        }
        for (String akCode : event.getAkCodes()) {
            try {
                ApikeyInfo apikey = apikeyRepo.queryByCode(akCode);
                if(apikey != null) {
                    apikeyService.clearApikeyCache(apikey.getAkSha());
                }
            } catch (Exception e) {
                log.warn("API Key变更后缓存清理失败 - akCode: {}, error: {}", akCode, e.getMessage(), e);
            }
        }
    }
}

package com.ke.bella.openapi.listener;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.apikey.ApikeyOps;
import com.ke.bella.openapi.db.repo.ApikeyRepo;
import com.ke.bella.openapi.event.ApiKeyTransferEvent;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.tables.pojos.ApikeyDB;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * API Key转移事件监听器
 * 负责处理API Key转移后的缓存清理等后续操作
 * 
 * @author claude
 */
@Slf4j
@Component
public class TransferApikeyListener {

    @Autowired
    private ApikeyRepo apikeyRepo;

    @Autowired
    private ApikeyService apikeyService;

    /**
     * 处理API Key转移事件
     * 异步执行缓存清理，确保不影响主要业务逻辑
     * 
     * @param event API Key转移事件
     */
    @Async("cacheTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleApiKeyTransfer(ApiKeyTransferEvent event) {
        try {
            LOGGER.info("开始处理API Key转移事件 - akCode: {}, from: {} -> to: {}, operator: {}({})",
                    event.getAkCode(), 
                    event.getFromOwnerName(), 
                    event.getToOwnerName(),
                    event.getOperatorName(),
                    event.getOperatorUid());
            
            clearTransferredApikeyCaches(event.getAkCode());

            LOGGER.info("API Key转移事件处理完成 - akCode: {}", event.getAkCode());
        } catch (Exception e) {
            LOGGER.warn("API Key转移后缓存清理失败，可能存在一定时间的脏数据 - akCode: {}, error: {}",
                    event.getAkCode(), e.getMessage(), e);
        }
    }

    /**
     * 清除转移后的API Key相关缓存
     * 包括主API Key和所有子API Key的缓存
     * 
     * @param akCode API Key编码
     */
    private void clearTransferredApikeyCaches(String akCode) {
        // 清除主API Key缓存
        ApikeyInfo mainApikey = apikeyRepo.queryByCode(akCode);
        if (mainApikey != null) {
            apikeyService.clearApikeyCache(mainApikey.getAkSha());
            LOGGER.debug("已清除主API Key缓存: akCode={}, akSha={}", akCode, mainApikey.getAkSha());
        }
        
        // 清除所有子API Key缓存
        ApikeyOps.ApikeyCondition condition = new ApikeyOps.ApikeyCondition();
        condition.setParentCode(akCode);
        List<ApikeyDB> subApikeys = apikeyRepo.listAccessKeys(condition);
        
        if (CollectionUtils.isNotEmpty(subApikeys)) {
            for (ApikeyDB subApikey : subApikeys) {
                apikeyService.clearApikeyCache(subApikey.getAkSha());
                LOGGER.debug("已清除子API Key缓存: akCode={}, akSha={}", subApikey.getCode(), subApikey.getAkSha());
            }
            LOGGER.info("API Key转移完成，共清除 {} 个子API Key缓存", subApikeys.size());
        } else {
            LOGGER.debug("API Key转移完成，无子API Key需要清除缓存");
        }
    }
}

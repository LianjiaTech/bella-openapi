package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.apikey.ApikeyTransferLog;
import com.ke.bella.openapi.tables.records.ApikeyTransferLogRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.ke.bella.openapi.Tables.APIKEY_TRANSFER_LOG;

/**
 * API Key转移日志数据访问层
 *
 * @author claude
 */
@Component
public class ApikeyTransferLogRepo implements BaseRepo {
    @Resource
    private DSLContext db;

    /**
     * 根据API Key编码查询转移历史
     *
     * @param akCode API Key编码
     * 
     * @return 转移历史列表
     */
    public List<ApikeyTransferLog> queryByAkCode(String akCode) {
        return db.select(APIKEY_TRANSFER_LOG.fields())
                .from(APIKEY_TRANSFER_LOG)
                .where(APIKEY_TRANSFER_LOG.AK_CODE.eq(akCode))
                .orderBy(APIKEY_TRANSFER_LOG.CTIME.desc())
                .fetchInto(ApikeyTransferLog.class);
    }

    /**
     * 插入转移日志
     *
     * @param log 转移日志信息
     * 
     * @return 插入的记录ID
     */
    public Long insertTransferLog(ApikeyTransferLog log) {
        ApikeyTransferLogRecord record = db.newRecord(APIKEY_TRANSFER_LOG);
        record.setAkCode(log.getAkCode());
        record.setFromOwnerType(log.getFromOwnerType());
        record.setFromOwnerCode(log.getFromOwnerCode());
        record.setFromOwnerName(log.getFromOwnerName());
        record.setToOwnerType(log.getToOwnerType());
        record.setToOwnerCode(log.getToOwnerCode());
        record.setToOwnerName(log.getToOwnerName());
        record.setTransferReason(log.getTransferReason());
        record.setStatus(log.getStatus());
        record.setOperatorUid(log.getOperatorUid());
        record.setOperatorName(log.getOperatorName());

        record.store();
        return record.getId();
    }
}

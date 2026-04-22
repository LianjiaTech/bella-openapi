package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.apikey.ApikeyChangeLog;
import com.ke.bella.openapi.tables.records.ApikeyChangeLogRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.ke.bella.openapi.Tables.APIKEY_CHANGE_LOG;

/**
 * API Key变更日志数据访问层
 */
@Component
public class ApikeyChangeLogRepo implements BaseRepo {

    @Resource
    private DSLContext db;

    public List<ApikeyChangeLog> queryByAkCode(String akCode) {
        return db.select(APIKEY_CHANGE_LOG.fields())
                .from(APIKEY_CHANGE_LOG)
                .where(APIKEY_CHANGE_LOG.AK_CODE.eq(akCode))
                .orderBy(APIKEY_CHANGE_LOG.CTIME.desc())
                .fetchInto(ApikeyChangeLog.class);
    }

    public void insert(ApikeyChangeLog log) {
        ApikeyChangeLogRecord record = db.newRecord(APIKEY_CHANGE_LOG);
        record.setActionType(log.getActionType());
        record.setAkCode(log.getAkCode());
        record.setAffectedCodes(log.getAffectedCodes());
        record.setFromOwnerType(log.getFromOwnerType());
        record.setFromOwnerCode(log.getFromOwnerCode());
        record.setFromOwnerName(log.getFromOwnerName());
        record.setToOwnerType(log.getToOwnerType());
        record.setToOwnerCode(log.getToOwnerCode());
        record.setToOwnerName(log.getToOwnerName());
        record.setFromParentCode(log.getFromParentCode());
        record.setToParentCode(log.getToParentCode());
        record.setFromManagerCode(log.getFromManagerCode());
        record.setFromManagerName(log.getFromManagerName());
        record.setToManagerCode(log.getToManagerCode());
        record.setToManagerName(log.getToManagerName());
        record.setReason(log.getReason());
        record.setStatus(log.getStatus());
        record.setOperatorUid(log.getOperatorUid());
        record.setOperatorName(log.getOperatorName());
        record.store();
    }
}

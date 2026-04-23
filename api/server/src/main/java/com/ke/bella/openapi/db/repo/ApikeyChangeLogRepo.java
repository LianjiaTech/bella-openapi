package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyChangeLog;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.apikey.ApikeyOps;
import com.ke.bella.openapi.tables.records.ApikeyChangeLogRecord;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;
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

    public void insertOwnerChangeLog(ApikeyOps.ChangeOwnerOp op, ApikeyInfo source, List<String> affectedCodes,
                                     String targetOwnerCode, String targetOwnerName, Operator currentOperator) {
        insert(ApikeyChangeLog.builder()
                .actionType("owner_change")
                .akCode(op.getCode())
                .affectedCodes(JacksonUtils.serialize(affectedCodes))
                .fromOwnerType(StringUtils.defaultString(source.getOwnerType()))
                .fromOwnerCode(StringUtils.defaultString(source.getOwnerCode()))
                .fromOwnerName(StringUtils.defaultString(source.getOwnerName()))
                .toOwnerType(StringUtils.defaultString(op.getTargetOwnerType()))
                .toOwnerCode(StringUtils.defaultString(targetOwnerCode))
                .toOwnerName(StringUtils.defaultString(targetOwnerName))
                .fromParentCode(StringUtils.defaultString(source.getParentCode()))
                .toParentCode(StringUtils.defaultString(source.getParentCode()))
                .fromManagerCode(StringUtils.defaultString(source.getManagerCode()))
                .fromManagerName(StringUtils.defaultString(source.getManagerName()))
                .toManagerCode(StringUtils.defaultString(source.getOwnerCode()))
                .toManagerName(StringUtils.defaultString(source.getOwnerName()))
                .reason(StringUtils.defaultString(op.getReason()))
                .status("completed")
                .operatorUid(currentOperator.getUserId())
                .operatorName(currentOperator.getUserName())
                .build());
    }

    public void insertParentChangeLog(ApikeyOps.ChangeParentOp op, ApikeyInfo source, List<String> affectedCodes,
                                      String toManagerCode, String toManagerName, Operator currentOperator) {
        insert(ApikeyChangeLog.builder()
                .actionType("parent_change")
                .akCode(op.getCode())
                .affectedCodes(JacksonUtils.serialize(affectedCodes))
                .fromOwnerType(StringUtils.defaultString(source.getOwnerType()))
                .fromOwnerCode(StringUtils.defaultString(source.getOwnerCode()))
                .fromOwnerName(StringUtils.defaultString(source.getOwnerName()))
                .toOwnerType(StringUtils.defaultString(source.getOwnerType()))
                .toOwnerCode(StringUtils.defaultString(source.getOwnerCode()))
                .toOwnerName(StringUtils.defaultString(source.getOwnerName()))
                .fromParentCode(StringUtils.defaultString(source.getParentCode()))
                .toParentCode(StringUtils.defaultString(op.getTargetParentCode()))
                .fromManagerCode(StringUtils.defaultString(source.getManagerCode()))
                .fromManagerName(StringUtils.defaultString(source.getManagerName()))
                .toManagerCode(StringUtils.defaultString(toManagerCode))
                .toManagerName(StringUtils.defaultString(toManagerName))
                .reason(StringUtils.defaultString(op.getReason()))
                .status("completed")
                .operatorUid(currentOperator.getUserId())
                .operatorName(currentOperator.getUserName())
                .build());
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

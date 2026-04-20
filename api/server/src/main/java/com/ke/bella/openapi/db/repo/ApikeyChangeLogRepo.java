package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.apikey.ApikeyChangeLog;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

@Component
public class ApikeyChangeLogRepo {

    private static final Table<?> APIKEY_CHANGE_LOG = table(name("apikey_change_log"));
    private static final Field<Long> ID = field(name("id"), Long.class);
    private static final Field<String> ACTION_TYPE = field(name("action_type"), String.class);
    private static final Field<String> AK_CODE = field(name("ak_code"), String.class);
    private static final Field<String> AFFECTED_CODES = field(name("affected_codes"), String.class);
    private static final Field<String> FROM_OWNER_TYPE = field(name("from_owner_type"), String.class);
    private static final Field<String> FROM_OWNER_CODE = field(name("from_owner_code"), String.class);
    private static final Field<String> FROM_OWNER_NAME = field(name("from_owner_name"), String.class);
    private static final Field<String> TO_OWNER_TYPE = field(name("to_owner_type"), String.class);
    private static final Field<String> TO_OWNER_CODE = field(name("to_owner_code"), String.class);
    private static final Field<String> TO_OWNER_NAME = field(name("to_owner_name"), String.class);
    private static final Field<String> FROM_PARENT_CODE = field(name("from_parent_code"), String.class);
    private static final Field<String> TO_PARENT_CODE = field(name("to_parent_code"), String.class);
    private static final Field<String> FROM_MANAGER_CODE = field(name("from_manager_code"), String.class);
    private static final Field<String> FROM_MANAGER_NAME = field(name("from_manager_name"), String.class);
    private static final Field<String> TO_MANAGER_CODE = field(name("to_manager_code"), String.class);
    private static final Field<String> TO_MANAGER_NAME = field(name("to_manager_name"), String.class);
    private static final Field<String> REASON = field(name("reason"), String.class);
    private static final Field<String> STATUS = field(name("status"), String.class);
    private static final Field<Long> OPERATOR_UID = field(name("operator_uid"), Long.class);
    private static final Field<String> OPERATOR_NAME = field(name("operator_name"), String.class);
    private static final Field<java.time.LocalDateTime> CTIME = field(name("ctime"), java.time.LocalDateTime.class);
    private static final Field<java.time.LocalDateTime> MTIME = field(name("mtime"), java.time.LocalDateTime.class);

    @Resource
    private DSLContext db;

    public List<ApikeyChangeLog> queryByAkCode(String akCode) {
        return db.select(
                        ID, ACTION_TYPE, AK_CODE, AFFECTED_CODES,
                        FROM_OWNER_TYPE, FROM_OWNER_CODE, FROM_OWNER_NAME,
                        TO_OWNER_TYPE, TO_OWNER_CODE, TO_OWNER_NAME,
                        FROM_PARENT_CODE, TO_PARENT_CODE,
                        FROM_MANAGER_CODE, FROM_MANAGER_NAME,
                        TO_MANAGER_CODE, TO_MANAGER_NAME,
                        REASON, STATUS, OPERATOR_UID, OPERATOR_NAME,
                        CTIME, MTIME
                )
                .from(APIKEY_CHANGE_LOG)
                .where(AK_CODE.eq(akCode))
                .orderBy(CTIME.desc())
                .fetchInto(ApikeyChangeLog.class);
    }

    public Long insert(ApikeyChangeLog log) {
        db.insertInto(APIKEY_CHANGE_LOG)
                .set(ACTION_TYPE, log.getActionType())
                .set(AK_CODE, log.getAkCode())
                .set(AFFECTED_CODES, log.getAffectedCodes())
                .set(FROM_OWNER_TYPE, log.getFromOwnerType())
                .set(FROM_OWNER_CODE, log.getFromOwnerCode())
                .set(FROM_OWNER_NAME, log.getFromOwnerName())
                .set(TO_OWNER_TYPE, log.getToOwnerType())
                .set(TO_OWNER_CODE, log.getToOwnerCode())
                .set(TO_OWNER_NAME, log.getToOwnerName())
                .set(FROM_PARENT_CODE, log.getFromParentCode())
                .set(TO_PARENT_CODE, log.getToParentCode())
                .set(FROM_MANAGER_CODE, log.getFromManagerCode())
                .set(FROM_MANAGER_NAME, log.getFromManagerName())
                .set(TO_MANAGER_CODE, log.getToManagerCode())
                .set(TO_MANAGER_NAME, log.getToManagerName())
                .set(REASON, log.getReason())
                .set(STATUS, log.getStatus())
                .set(OPERATOR_UID, log.getOperatorUid())
                .set(OPERATOR_NAME, log.getOperatorName())
                .execute();
        return null;
    }
}

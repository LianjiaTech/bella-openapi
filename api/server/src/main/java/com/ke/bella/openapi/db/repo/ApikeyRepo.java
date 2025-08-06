package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.apikey.ApikeyOps;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.tables.pojos.ApikeyDB;
import com.ke.bella.openapi.tables.records.ApikeyRecord;
import org.apache.commons.lang3.StringUtils;
import org.jooq.SelectSeekStep1;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ke.bella.openapi.Tables.APIKEY;
import static com.ke.bella.openapi.Tables.APIKEY_ROLE;

@Component
public class ApikeyRepo extends StatusRepo<ApikeyDB, ApikeyRecord, String> implements AutogenCodeRepo<ApikeyRecord> {

    public ApikeyInfo queryBySha(String sha) {
        return db.select(APIKEY.fields())
                .select(APIKEY_ROLE.PATH).from(APIKEY)
                .leftJoin(APIKEY_ROLE).on(APIKEY.ROLE_CODE.eq(APIKEY_ROLE.ROLE_CODE))
                .where(APIKEY.AK_SHA.eq(sha))
                .fetchOneInto(ApikeyInfo.class);
    }

    public ApikeyInfo queryByCode(String code) {
        return db.select(APIKEY.fields())
                .select(APIKEY_ROLE.PATH).from(APIKEY)
                .leftJoin(APIKEY_ROLE).on(APIKEY.ROLE_CODE.eq(APIKEY_ROLE.ROLE_CODE))
                .where(APIKEY.CODE.eq(code))
                .fetchOneInto(ApikeyInfo.class);
    }

    public void updateRoleBySha(String sha, String roleCode) {
        db.update(APIKEY)
                .set(APIKEY.ROLE_CODE, roleCode)
                .where(APIKEY.AK_SHA.eq(sha))
                .execute();
    }

    public List<ApikeyDB> listAccessKeys(ApikeyOps.ApikeyCondition op) {
        return constructSql(op).fetchInto(ApikeyDB.class);
    }

    public Page<ApikeyDB> pageAccessKeys(ApikeyOps.ApikeyCondition op) {
        return queryPage(db, constructSql(op), op.getPage(), op.getSize(), ApikeyDB.class);
    }

    private SelectSeekStep1<ApikeyRecord, Long> constructSql(ApikeyOps.ApikeyCondition op) {
        return db.selectFrom(APIKEY)
                .where(StringUtils.isEmpty(op.getOwnerType()) ? DSL.noCondition() : APIKEY.OWNER_TYPE.eq(op.getOwnerType()))
                .and(StringUtils.isEmpty(op.getOwnerCode()) ? DSL.noCondition() : APIKEY.OWNER_CODE.eq(op.getOwnerCode()))
                .and(StringUtils.isEmpty(op.getParentCode()) ? DSL.noCondition() : APIKEY.PARENT_CODE.eq(op.getParentCode()))
                .and(StringUtils.isEmpty(op.getName()) ? DSL.noCondition() : APIKEY.NAME.eq(op.getName()))
                .and(StringUtils.isEmpty(op.getServiceId()) ? DSL.noCondition() : APIKEY.SERVICE_ID.eq(op.getServiceId()))
                .and(StringUtils.isEmpty(op.getOutEntityCode()) ? DSL.noCondition() : APIKEY.OUT_ENTITY_CODE.eq(op.getOutEntityCode()))
                .and(StringUtils.isEmpty(op.getSearchParam()) ? DSL.noCondition() : APIKEY.NAME.like(op.getSearchParam() + "%")
                        .or(APIKEY.SERVICE_ID.like(op.getSearchParam() + "%")))
                .and(op.isIncludeChild() || StringUtils.isNotEmpty(op.getParentCode()) ? DSL.noCondition() : APIKEY.PARENT_CODE.eq(StringUtils.EMPTY))
                .and(StringUtils.isEmpty(op.getStatus()) ? DSL.noCondition() : APIKEY.STATUS.eq(op.getStatus()))
                .and(StringUtils.isEmpty(op.getPersonalCode()) ? DSL.noCondition() :
                                APIKEY.OWNER_TYPE.eq(EntityConstants.PERSON).and(APIKEY.OWNER_CODE.eq(op.getPersonalCode())))
                .orderBy(APIKEY.ID.desc());
    }

    @Override
    public TableField<ApikeyRecord, String> autoCode() {
        return APIKEY.CODE;
    }

    @Override
    public String prefix() {
        return "ak-";
    }

    @Override
    protected TableField<ApikeyRecord, String> statusFiled() {
        return APIKEY.STATUS;
    }

    @Override
    protected TableImpl<ApikeyRecord> table() {
        return APIKEY;
    }

    @Override
    protected TableField<ApikeyRecord, String> uniqueKey() {
        return APIKEY.CODE;
    }
    
    /**
     * 批量更新子API Key的所有者信息
     * @param updateDB 更新的字段信息
     * @param parentCode 父API Key的code
     * @return 更新的记录数
     */
    @Transactional
    public int batchUpdateByParentCode(ApikeyDB updateDB, String parentCode) {
        return db.update(APIKEY)
                .set(APIKEY.OWNER_TYPE, updateDB.getOwnerType())
                .set(APIKEY.OWNER_CODE, updateDB.getOwnerCode())
                .set(APIKEY.OWNER_NAME, updateDB.getOwnerName())
                .set(APIKEY.MUID, updateDB.getMuid())
                .set(APIKEY.MU_NAME, updateDB.getMuName())
                .where(APIKEY.PARENT_CODE.eq(parentCode))
                .execute();
    }
}

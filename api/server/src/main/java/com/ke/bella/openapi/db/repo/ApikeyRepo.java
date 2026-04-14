package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.apikey.ApikeyOps;
import com.ke.bella.openapi.apikey.ApikeyWithBalance;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.tables.pojos.ApikeyDB;
import com.ke.bella.openapi.tables.records.ApikeyRecord;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.SelectSeekStep1;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static com.ke.bella.openapi.Tables.APIKEY;
import static com.ke.bella.openapi.Tables.APIKEY_MONTH_COST;
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
                .and(StringUtils.isEmpty(op.getSearchParam()) ? DSL.noCondition()
                        : APIKEY.NAME.like(op.getSearchParam() + "%")
                                .or(APIKEY.SERVICE_ID.like(op.getSearchParam() + "%")))
                .and(StringUtils.isEmpty(op.getOwnerSearch()) ? DSL.noCondition()
                        : APIKEY.OWNER_NAME.like(op.getOwnerSearch() + "%")
                                .or(APIKEY.OWNER_CODE.like(op.getOwnerSearch() + "%")))
                .and(op.isIncludeChild() || StringUtils.isNotEmpty(op.getParentCode()) ? DSL.noCondition() : APIKEY.PARENT_CODE.eq(StringUtils.EMPTY))
                .and(StringUtils.isEmpty(op.getStatus()) ? DSL.noCondition() : APIKEY.STATUS.eq(op.getStatus()))
                .and(StringUtils.isEmpty(op.getPersonalCode()) ? DSL.noCondition()
                        : APIKEY.OWNER_TYPE.eq(EntityConstants.PERSON).and(APIKEY.OWNER_CODE.eq(op.getPersonalCode())))
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
     *
     * @param updateDB   更新的字段信息
     * @param parentCode 父API Key的code
     *
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

    /**
     * 分页查询 API Key 列表并关联当月消费（批量查询，避免 N+1 问题）
     * @param op           查询条件（与 pageAccessKeys 参数完全一致）
     * @param currentMonth 当前月份，格式：2026-04
     * @return 带余额信息的 API Key 分页结果
     */
    public Page<ApikeyWithBalance> pageApikeysWithBalance(
            ApikeyOps.ApikeyCondition op,
            String currentMonth
    ) {
        // 构建查询条件（复用现有逻辑）
        Condition conditions = buildConditions(op);

        // 构建 LEFT JOIN 查询
        org.jooq.SelectSeekStep1<org.jooq.Record, Long> query = db.select(
                        // API Key 基础字段（24个）
                        APIKEY.ID,
                        APIKEY.CODE,
                        APIKEY.AK_SHA,
                        APIKEY.AK_DISPLAY,
                        APIKEY.NAME,
                        APIKEY.PARENT_CODE,
                        APIKEY.OUT_ENTITY_CODE,
                        APIKEY.SERVICE_ID,
                        APIKEY.OWNER_TYPE,
                        APIKEY.OWNER_CODE,
                        APIKEY.OWNER_NAME,
                        APIKEY.ROLE_CODE,
                        APIKEY.CERTIFY_CODE,
                        APIKEY.SAFETY_SCENE_CODE,
                        APIKEY.SAFETY_LEVEL,
                        APIKEY.MONTH_QUOTA,
                        APIKEY.STATUS,
                        APIKEY.REMARK,
                        APIKEY.CUID,
                        APIKEY.CU_NAME,
                        APIKEY.MUID,
                        APIKEY.MU_NAME,
                        APIKEY.CTIME,
                        APIKEY.MTIME,
                        // 当前月份（常量）
                        DSL.val(currentMonth).as("currentMonth"),
                        // 当月消费（分→元，NULL 处理，保留 2 位小数）
                        DSL.nvl(APIKEY_MONTH_COST.AMOUNT, BigDecimal.ZERO)
                                .divide(100)
                                .round(2)
                                .as("monthCost")
                )
                .from(APIKEY)
                // LEFT JOIN 关联月度消费表
                .leftJoin(APIKEY_MONTH_COST)
                .on(APIKEY.CODE.eq(APIKEY_MONTH_COST.AK_CODE)
                        .and(APIKEY_MONTH_COST.MONTH.eq(currentMonth)))
                // 应用查询条件
                .where(conditions)
                // 按 ID 降序排序
                .orderBy(APIKEY.ID.desc());

        // 执行分页查询并映射到 ApikeyWithBalance
        return queryPage(db, query, op.getPage(), op.getSize(), ApikeyWithBalance.class);
    }

    /**
     * 构建查询条件（从 constructSql 中提取的公共逻辑）
     *
     * <p>职责：
     * <ul>
     *   <li>封装所有查询条件的构建逻辑</li>
     *   <li>供 pageAccessKeys 和 pageApikeysWithBalance 复用</li>
     *   <li>确保两个方法的查询条件完全一致</li>
     * </ul>
     *
     * <p>代码设计：
     * <ul>
     *   <li>使用 jOOQ 的 Condition 类型构建条件链</li>
     *   <li>空值条件使用 DSL.noCondition() 跳过</li>
     *   <li>所有条件使用 AND 连接</li>
     *   <li>支持模糊搜索（LIKE）和精确匹配（EQ）</li>
     * </ul>
     *
     * @param op 查询条件
     * @return jOOQ Condition 对象
     */
    private Condition buildConditions(ApikeyOps.ApikeyCondition op) {
        return DSL.noCondition()
                // 所有者类型
                .and(StringUtils.isEmpty(op.getOwnerType()) ? DSL.noCondition()
                        : APIKEY.OWNER_TYPE.eq(op.getOwnerType()))
                // 所有者编码
                .and(StringUtils.isEmpty(op.getOwnerCode()) ? DSL.noCondition()
                        : APIKEY.OWNER_CODE.eq(op.getOwnerCode()))
                // 父 API Key
                .and(StringUtils.isEmpty(op.getParentCode()) ? DSL.noCondition()
                        : APIKEY.PARENT_CODE.eq(op.getParentCode()))
                // API Key 名称
                .and(StringUtils.isEmpty(op.getName()) ? DSL.noCondition()
                        : APIKEY.NAME.eq(op.getName()))
                // 服务 ID
                .and(StringUtils.isEmpty(op.getServiceId()) ? DSL.noCondition()
                        : APIKEY.SERVICE_ID.eq(op.getServiceId()))
                // 授权实体编码
                .and(StringUtils.isEmpty(op.getOutEntityCode()) ? DSL.noCondition()
                        : APIKEY.OUT_ENTITY_CODE.eq(op.getOutEntityCode()))
                // 搜索参数（名称或服务 ID）
                .and(StringUtils.isEmpty(op.getSearchParam()) ? DSL.noCondition()
                        : APIKEY.NAME.like(op.getSearchParam() + "%")
                        .or(APIKEY.SERVICE_ID.like(op.getSearchParam() + "%")))
                // 所有者搜索（所有者名称或编码）
                .and(StringUtils.isEmpty(op.getOwnerSearch()) ? DSL.noCondition()
                        : APIKEY.OWNER_NAME.like(op.getOwnerSearch() + "%")
                        .or(APIKEY.OWNER_CODE.like(op.getOwnerSearch() + "%")))
                // 是否包含子 Key
                .and(op.isIncludeChild() || StringUtils.isNotEmpty(op.getParentCode()) ? DSL.noCondition()
                        : APIKEY.PARENT_CODE.eq(StringUtils.EMPTY))
                // 状态
                .and(StringUtils.isEmpty(op.getStatus()) ? DSL.noCondition()
                        : APIKEY.STATUS.eq(op.getStatus()))
                // 个人编码（特殊条件：类型为 person 且编码匹配）
                .and(StringUtils.isEmpty(op.getPersonalCode()) ? DSL.noCondition()
                        : APIKEY.OWNER_TYPE.eq(EntityConstants.PERSON)
                        .and(APIKEY.OWNER_CODE.eq(op.getPersonalCode())));
    }
}

package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.tables.pojos.CategoryDB;
import com.ke.bella.openapi.tables.pojos.EndpointCategoryRelDB;
import com.ke.bella.openapi.tables.records.CategoryRecord;
import com.ke.bella.openapi.tables.records.EndpointCategoryRelRecord;
import org.apache.commons.lang3.StringUtils;
import org.jooq.SelectSeekStep1;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.ke.bella.openapi.Tables.ENDPOINT_CATEGORY_REL;
import static com.ke.bella.openapi.tables.Category.CATEGORY;

/**
 * Author: Stan Sai Date: 2024/8/1 21:13 description:
 */
@Component
public class CategoryRepo extends StatusRepo<CategoryDB, CategoryRecord, String> {

    @Override
    protected CategoryRecord getRecForInsert(Object op) {
        CategoryRecord rec = super.getRecForInsert(op);
        try {
            Field parentFiled = op.getClass().getDeclaredField("parentCode");
            parentFiled.setAccessible(true);
            Object prentCode = ReflectionUtils.getField(parentFiled, op);
            String code = generateCategoryCode(prentCode == null ? "" : prentCode.toString());
            code = prentCode == null ? code : prentCode + "-" + code;
            rec.setCategoryCode(code);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("参数错误，缺少parentCode");
        }
        return rec;
    }

    private String generateCategoryCode(String parentCode) {
        String code = db.selectFrom(CATEGORY)
                .where(CATEGORY.PARENT_CODE.eq(parentCode))
                .orderBy(CATEGORY.ID.desc())
                .limit(1)
                .forUpdate()
                .fetchOne(CATEGORY.CATEGORY_CODE);
        if(code == null) {
            return "0001";
        }
        String[] codes = code.split("-");
        String maxCode = codes[codes.length - 1];
        Assert.isTrue(!maxCode.equals("9999"), "类目超过最大数量");
        return String.format("%04d", Integer.parseInt(maxCode) + 1);
    }

    @Transactional
    public int batchInsertRelations(String endpoint, Collection<String> categoryCodes) {
        List<EndpointCategoryRelRecord> records = categoryCodes.stream()
                .map(code -> constructRecord(endpoint, code)).collect(Collectors.toList());
        return batchInsert(db, records);
    }

    public EndpointCategoryRelRecord constructRecord(String endpoint, String categoryCode) {
        EndpointCategoryRelRecord rec = ENDPOINT_CATEGORY_REL.newRecord();
        rec.setEndpoint(endpoint);
        rec.setCategoryCode(categoryCode);
        fillCreatorInfo(rec);
        return rec;
    }

    @Transactional
    public int deleteRelation(Long id) {
        return db.deleteFrom(ENDPOINT_CATEGORY_REL)
                .where(ENDPOINT_CATEGORY_REL.ID.eq(id))
                .execute();
    }

    @Transactional
    public int deleteRelations(List<Long> ids) {
        return db.deleteFrom(ENDPOINT_CATEGORY_REL)
                .where(ENDPOINT_CATEGORY_REL.ID.in(ids))
                .execute();
    }

    public CategoryDB queryByParentCodeAndName(String parentCode, String name) {
        return db.selectFrom(CATEGORY)
                .where(CATEGORY.PARENT_CODE.eq(parentCode))
                .and(CATEGORY.CATEGORY_NAME.eq(name))
                .fetchOneInto(CategoryDB.class);
    }

    public List<CategoryDB> queryAllChildrenIncludeSelfByCategoryCode(String categoryCode, String status) {
        return db.selectFrom(CATEGORY)
                .where(CATEGORY.CATEGORY_CODE.like(categoryCode + "%"))
                .and(StringUtils.isEmpty(status) ? DSL.noCondition() : CATEGORY.STATUS.eq(status))
                .orderBy(CATEGORY.CATEGORY_CODE)
                .fetchInto(CategoryDB.class);
    }

    public long countChildrenByCategoryCode(String categoryCode, String status) {
        return db.selectFrom(CATEGORY)
                .where(CATEGORY.CATEGORY_CODE.like(categoryCode + "%"))
                .and(CATEGORY.CATEGORY_CODE.ne(categoryCode))
                .and(StringUtils.isEmpty(status) ? DSL.noCondition() : CATEGORY.STATUS.eq(status))
                .stream().count();
    }

    public EndpointCategoryRelDB queryByEndpointAndCategoryCode(String endpoint, String categoryCode) {
        return db.selectFrom(ENDPOINT_CATEGORY_REL)
                .where(ENDPOINT_CATEGORY_REL.ENDPOINT.eq(endpoint))
                .and(ENDPOINT_CATEGORY_REL.CATEGORY_CODE.eq(categoryCode))
                .fetchOneInto(EndpointCategoryRelDB.class);
    }

    public List<EndpointCategoryRelDB> listEndpointCategoriesByCategoryCodes(List<String> categoryCodes) {
        return db.selectFrom(ENDPOINT_CATEGORY_REL)
                .where(ENDPOINT_CATEGORY_REL.CATEGORY_CODE.in(categoryCodes))
                .orderBy(ENDPOINT_CATEGORY_REL.CATEGORY_CODE)
                .fetchInto(EndpointCategoryRelDB.class);
    }

    public List<EndpointCategoryRelDB> listEndpointCategoriesByEndpoint(String endpoint) {
        return db.selectFrom(ENDPOINT_CATEGORY_REL)
                .where(ENDPOINT_CATEGORY_REL.ENDPOINT.eq(endpoint))
                .fetchInto(EndpointCategoryRelDB.class);
    }

    public long countEndpointCategoriesByCategoryCode(String categoryCode) {
        return db.selectFrom(ENDPOINT_CATEGORY_REL)
                .where(ENDPOINT_CATEGORY_REL.CATEGORY_CODE.eq(categoryCode))
                .stream().count();
    }

    public long count(Condition.CategoryCondition op) {
        return constructSql(op).stream().count();
    }

    public List<CategoryDB> list(Condition.CategoryCondition op) {
        return constructSql(op).fetchInto(CategoryDB.class);
    }

    public Page<CategoryDB> page(Condition.CategoryCondition op) {
        return queryPage(db, constructSql(op), op.getPage(), op.getSize(), CategoryDB.class);
    }

    private SelectSeekStep1<CategoryRecord, String> constructSql(Condition.CategoryCondition op) {
        return db.selectFrom(CATEGORY)
                .where(StringUtils.isEmpty(op.getCategoryCode()) ? DSL.noCondition() : CATEGORY.CATEGORY_CODE.eq(op.getCategoryCode()))
                .and(StringUtils.isEmpty(op.getCategoryName())
                        ? DSL.noCondition()
                        : CATEGORY.CATEGORY_NAME.like("%" + op.getCategoryName() + "%"))
                .and(StringUtils.isEmpty(op.getParentCode()) ? DSL.noCondition() : CATEGORY.PARENT_CODE.eq(op.getParentCode()))
                .and(Boolean.TRUE.equals(op.getTopCategory()) ? CATEGORY.PARENT_CODE.eq("") : DSL.noCondition())
                .and(StringUtils.isEmpty(op.getStatus()) ? DSL.noCondition() : CATEGORY.STATUS.eq(op.getStatus()))
                .orderBy(CATEGORY.CATEGORY_CODE);
    }

    @Override
    public TableImpl<CategoryRecord> table() {
        return CATEGORY;
    }

    @Override
    protected TableField<CategoryRecord, String> uniqueKey() {
        return CATEGORY.CATEGORY_CODE;
    }

    @Override
    protected TableField<CategoryRecord, String> statusFiled() {
        return CATEGORY.STATUS;
    }
}

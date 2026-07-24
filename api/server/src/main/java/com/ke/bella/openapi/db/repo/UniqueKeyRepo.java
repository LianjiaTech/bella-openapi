package com.ke.bella.openapi.db.repo;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.TableField;
import org.jooq.impl.TableImpl;
import org.jooq.impl.UpdatableRecordImpl;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class UniqueKeyRepo<T extends Operator, R extends UpdatableRecordImpl<R>, K> implements BaseRepo {
    @Resource
    protected DSLContext db;

    protected abstract TableImpl<R> table();

    protected abstract TableField<R, K> uniqueKey();

    @Transactional
    public T insert(Object op) {
        R rec = getRecForInsert(op);
        db.insertInto(table())
                .set(rec)
                .execute();
        return rec.into(entityClass());
    }

    @Transactional
    public T insertIgnoreDuplicateKey(Object op) {
        R rec = getRecForInsert(op);
        int num = db.insertInto(table())
                .set(rec)
                .onDuplicateKeyIgnore()
                .execute();
        return num == 1 ? rec.into(entityClass()) : null;
    }

    protected R getRecForInsert(Object op) {
        R rec = table().newRecord();
        rec.from(op);
        if(AutogenCodeRepo.class.isAssignableFrom(getClass())) {
            ((AutogenCodeRepo) this).autogen(rec);
        }
        fillCreatorInfo(rec);
        return rec;
    }

    @Transactional
    public void update(Object op, K uniqueKey) {
        R rec = table().newRecord();
        rec.from(op);
        fillUpdatorInfo(rec);
        int num = db.update(table())
                .set(rec)
                .where(uniqueKey().eq(uniqueKey))
                .execute();
        Assert.isTrue(num == 1, "实体更新失败，请检查实体是否存在");
    }

    public T queryByUniqueKey(K uniqueKey) {
        return db.selectFrom(table())
                .where(uniqueKey().eq(uniqueKey))
                .fetchOneInto(entityClass());
    }

    public <H> H queryByUniqueKey(K uniqueKey, Class<H> type) {
        return db.selectFrom(table())
                .where(uniqueKey().eq(uniqueKey))
                .fetchOneInto(type);
    }

    @Transactional
    public T queryByUniqueKeyForUpdate(K uniqueKey) {
        return db.selectFrom(table())
                .where(uniqueKey().eq(uniqueKey))
                .forUpdate()
                .fetchOneInto(entityClass());
    }

    @Transactional
    public T queryByUniqueKeyForUpdateNoWait(K uniqueKey) {
        return db.selectFrom(table())
                .where(uniqueKey().eq(uniqueKey))
                .forUpdate()
                .noWait()
                .fetchOneInto(entityClass());
    }

    @Transactional
    public T queryByUniqueKeyForUpdateSkipLocked(K uniqueKey) {
        return db.selectFrom(table())
                .where(uniqueKey().eq(uniqueKey))
                .forUpdate()
                .skipLocked()
                .fetchOneInto(entityClass());
    }

    public void checkExist(K uniqueKey, boolean exist) {
        Assert.isTrue((queryByUniqueKey(uniqueKey) != null) == exist,
                exist ? "实体不存在" : "实体已存在");
    }

    private Class<T> entityClass() {
        Type type = getClass().getGenericSuperclass();
        ParameterizedType parameterizedType = (ParameterizedType) type;
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        return (Class<T>) actualTypeArguments[0];
    }

}

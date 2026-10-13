package com.ke.bella.openapi.db.repo;

import static com.ke.bella.openapi.Tables.*;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Resource;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ke.bella.openapi.tables.pojos.ApikeyMonthCostDB;
import com.ke.bella.openapi.tables.records.ApikeyMonthCostRecord;

@Component
public class ApikeyCostRepo implements BaseRepo {
    @Resource
    private DSLContext db;

    @Transactional
    public void insert(String akCode, String month) {
        ApikeyMonthCostRecord rec = APIKEY_MONTH_COST.newRecord();
        rec.setAkCode(akCode);
        rec.setMonth(month);
        rec.setAmount(BigDecimal.ZERO);

        db.insertInto(APIKEY_MONTH_COST).set(rec)
                .onDuplicateKeyIgnore()
                .execute();
    }

    @Transactional
    public void increment(String akCode, String month, BigDecimal cost) {
        db.update(APIKEY_MONTH_COST)
                .set(APIKEY_MONTH_COST.AMOUNT, APIKEY_MONTH_COST.AMOUNT.add(cost))
                .where(APIKEY_MONTH_COST.AK_CODE.eq(akCode))
                .and(APIKEY_MONTH_COST.MONTH.eq(month))
                .execute();
    }

    public BigDecimal queryCost(String akCode, String month) {
        return db.select(APIKEY_MONTH_COST.AMOUNT)
                .from(APIKEY_MONTH_COST)
                .where(APIKEY_MONTH_COST.AK_CODE.eq(akCode))
                .and(APIKEY_MONTH_COST.MONTH.eq(month))
                .fetchOneInto(BigDecimal.class);
    }

    public BigDecimal refreshCache(String akCode, String month) {
        return queryCost(akCode, month);
    }

    public List<ApikeyMonthCostDB> queryByAkCode(String akCode) {
        return db.selectFrom(APIKEY_MONTH_COST)
                .where(APIKEY_MONTH_COST.AK_CODE.eq(akCode))
                .fetchInto(ApikeyMonthCostDB.class);
    }
}

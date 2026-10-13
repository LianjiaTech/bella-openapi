package com.ke.bella.openapi;

import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CachePenetrationProtect;
import com.alicp.jetcache.anno.CacheRefresh;
import com.alicp.jetcache.anno.Cached;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;

import static com.ke.bella.openapi.Tables.APIKEY_MONTH_COST;

@Component
public class CacheRepoTest {
    @Resource
    private DSLContext db;
    private BigDecimal amount = null;

    @Cached(name = "testCache:",
            key = "#apikey + ':' + #month",
            cacheNullValue = true,
            condition = "T(com.ke.bella.openapi.utils.DateTimeUtils).isCurrentMonth(#month)")
    @CachePenetrationProtect(timeout = 5)
    @CacheRefresh(refresh = 5, stopRefreshAfterLastAccess = 60)
    public BigDecimal get(String apikey, String month) {
        System.out.println("query db");
        return amount;
    }

    public void add(String apikey, String month, BigDecimal cost) {
        if(amount == null) {
            amount = cost;
        } else {
            amount = amount.add(cost);
        }
        System.out.println("update db " + amount.doubleValue());
    }

    @CacheInvalidate(name = "testCache",
            key = "#apikey + ':' + #month")
    public void delete(String apikey, String month) {
        amount = null;
        System.out.println("delete");
    }

    @Transactional
    @CacheInvalidate(name = "apikey:cost:month:", key = "#akCode + ':' + #month")
    public boolean deleteCost(String akCode, String month) {
        return db.deleteFrom(APIKEY_MONTH_COST)
                .where(APIKEY_MONTH_COST.AK_CODE.eq(akCode))
                .and(APIKEY_MONTH_COST.MONTH.eq(month))
                .execute() > 0;
    }
}


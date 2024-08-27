/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables.records;


import com.ke.bella.openapi.db.repo.Timed;
import com.ke.bella.openapi.tables.ApiKeyMonthCost;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record6;
import org.jooq.Row6;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * ak月花费
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ApiKeyMonthCostRecord extends UpdatableRecordImpl<ApiKeyMonthCostRecord> implements Timed, Record6<Long, String, String, BigDecimal, LocalDateTime, LocalDateTime> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>api_key_month_cost.id</code>. 主键ID
     */
    public void setId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>api_key_month_cost.id</code>. 主键ID
     */
    public Long getId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>api_key_month_cost.ak_code</code>. ak编码
     */
    public void setAkCode(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>api_key_month_cost.ak_code</code>. ak编码
     */
    public String getAkCode() {
        return (String) get(1);
    }

    /**
     * Setter for <code>api_key_month_cost.month</code>. 月份
     */
    public void setMonth(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>api_key_month_cost.month</code>. 月份
     */
    public String getMonth() {
        return (String) get(2);
    }

    /**
     * Setter for <code>api_key_month_cost.amount</code>. 开销（分）
     */
    public void setAmount(BigDecimal value) {
        set(3, value);
    }

    /**
     * Getter for <code>api_key_month_cost.amount</code>. 开销（分）
     */
    public BigDecimal getAmount() {
        return (BigDecimal) get(3);
    }

    /**
     * Setter for <code>api_key_month_cost.ctime</code>.
     */
    public void setCtime(LocalDateTime value) {
        set(4, value);
    }

    /**
     * Getter for <code>api_key_month_cost.ctime</code>.
     */
    public LocalDateTime getCtime() {
        return (LocalDateTime) get(4);
    }

    /**
     * Setter for <code>api_key_month_cost.mtime</code>.
     */
    public void setMtime(LocalDateTime value) {
        set(5, value);
    }

    /**
     * Getter for <code>api_key_month_cost.mtime</code>.
     */
    public LocalDateTime getMtime() {
        return (LocalDateTime) get(5);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record6 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row6<Long, String, String, BigDecimal, LocalDateTime, LocalDateTime> fieldsRow() {
        return (Row6) super.fieldsRow();
    }

    @Override
    public Row6<Long, String, String, BigDecimal, LocalDateTime, LocalDateTime> valuesRow() {
        return (Row6) super.valuesRow();
    }

    @Override
    public Field<Long> field1() {
        return ApiKeyMonthCost.API_KEY_MONTH_COST.ID;
    }

    @Override
    public Field<String> field2() {
        return ApiKeyMonthCost.API_KEY_MONTH_COST.AK_CODE;
    }

    @Override
    public Field<String> field3() {
        return ApiKeyMonthCost.API_KEY_MONTH_COST.MONTH;
    }

    @Override
    public Field<BigDecimal> field4() {
        return ApiKeyMonthCost.API_KEY_MONTH_COST.AMOUNT;
    }

    @Override
    public Field<LocalDateTime> field5() {
        return ApiKeyMonthCost.API_KEY_MONTH_COST.CTIME;
    }

    @Override
    public Field<LocalDateTime> field6() {
        return ApiKeyMonthCost.API_KEY_MONTH_COST.MTIME;
    }

    @Override
    public Long component1() {
        return getId();
    }

    @Override
    public String component2() {
        return getAkCode();
    }

    @Override
    public String component3() {
        return getMonth();
    }

    @Override
    public BigDecimal component4() {
        return getAmount();
    }

    @Override
    public LocalDateTime component5() {
        return getCtime();
    }

    @Override
    public LocalDateTime component6() {
        return getMtime();
    }

    @Override
    public Long value1() {
        return getId();
    }

    @Override
    public String value2() {
        return getAkCode();
    }

    @Override
    public String value3() {
        return getMonth();
    }

    @Override
    public BigDecimal value4() {
        return getAmount();
    }

    @Override
    public LocalDateTime value5() {
        return getCtime();
    }

    @Override
    public LocalDateTime value6() {
        return getMtime();
    }

    @Override
    public ApiKeyMonthCostRecord value1(Long value) {
        setId(value);
        return this;
    }

    @Override
    public ApiKeyMonthCostRecord value2(String value) {
        setAkCode(value);
        return this;
    }

    @Override
    public ApiKeyMonthCostRecord value3(String value) {
        setMonth(value);
        return this;
    }

    @Override
    public ApiKeyMonthCostRecord value4(BigDecimal value) {
        setAmount(value);
        return this;
    }

    @Override
    public ApiKeyMonthCostRecord value5(LocalDateTime value) {
        setCtime(value);
        return this;
    }

    @Override
    public ApiKeyMonthCostRecord value6(LocalDateTime value) {
        setMtime(value);
        return this;
    }

    @Override
    public ApiKeyMonthCostRecord values(Long value1, String value2, String value3, BigDecimal value4, LocalDateTime value5, LocalDateTime value6) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached ApiKeyMonthCostRecord
     */
    public ApiKeyMonthCostRecord() {
        super(ApiKeyMonthCost.API_KEY_MONTH_COST);
    }

    /**
     * Create a detached, initialised ApiKeyMonthCostRecord
     */
    public ApiKeyMonthCostRecord(Long id, String akCode, String month, BigDecimal amount, LocalDateTime ctime, LocalDateTime mtime) {
        super(ApiKeyMonthCost.API_KEY_MONTH_COST);

        setId(id);
        setAkCode(akCode);
        setMonth(month);
        setAmount(amount);
        setCtime(ctime);
        setMtime(mtime);
    }
}

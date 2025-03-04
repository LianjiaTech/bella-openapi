/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables.records;


import com.ke.bella.openapi.db.repo.Operator;
import com.ke.bella.openapi.tables.Channel;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * 通道
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ChannelRecord extends UpdatableRecordImpl<ChannelRecord> implements Operator {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>channel.id</code>. 主键ID
     */
    public void setId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>channel.id</code>. 主键ID
     */
    public Long getId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>channel.entity_type</code>. 实体类型（endpoint/model）
     */
    public void setEntityType(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>channel.entity_type</code>. 实体类型（endpoint/model）
     */
    public String getEntityType() {
        return (String) get(1);
    }

    /**
     * Setter for <code>channel.entity_code</code>. 实体编码
     */
    public void setEntityCode(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>channel.entity_code</code>. 实体编码
     */
    public String getEntityCode() {
        return (String) get(2);
    }

    /**
     * Setter for <code>channel.channel_code</code>. 渠道编码
     */
    public void setChannelCode(String value) {
        set(3, value);
    }

    /**
     * Getter for <code>channel.channel_code</code>. 渠道编码
     */
    public String getChannelCode() {
        return (String) get(3);
    }

    /**
     * Setter for <code>channel.status</code>. 状态状态(active/inactive)
     */
    public void setStatus(String value) {
        set(4, value);
    }

    /**
     * Getter for <code>channel.status</code>. 状态状态(active/inactive)
     */
    public String getStatus() {
        return (String) get(4);
    }

    /**
     * Setter for <code>channel.owner_type</code>. 所有者类型（组织/个人）
     */
    public void setOwnerType(String value) {
        set(5, value);
    }

    /**
     * Getter for <code>channel.owner_type</code>. 所有者类型（组织/个人）
     */
    public String getOwnerType() {
        return (String) get(5);
    }

    /**
     * Setter for <code>channel.owner_code</code>. 所有者系统号
     */
    public void setOwnerCode(String value) {
        set(6, value);
    }

    /**
     * Getter for <code>channel.owner_code</code>. 所有者系统号
     */
    public String getOwnerCode() {
        return (String) get(6);
    }

    /**
     * Setter for <code>channel.owner_name</code>. 所有者名称
     */
    public void setOwnerName(String value) {
        set(7, value);
    }

    /**
     * Getter for <code>channel.owner_name</code>. 所有者名称
     */
    public String getOwnerName() {
        return (String) get(7);
    }

    /**
     * Setter for <code>channel.visibility</code>. 是否公开(private/public)
     */
    public void setVisibility(String value) {
        set(8, value);
    }

    /**
     * Getter for <code>channel.visibility</code>. 是否公开(private/public)
     */
    public String getVisibility() {
        return (String) get(8);
    }

    /**
     * Setter for <code>channel.trial_enabled</code>. 是否支持试用
     */
    public void setTrialEnabled(Byte value) {
        set(9, value);
    }

    /**
     * Getter for <code>channel.trial_enabled</code>. 是否支持试用
     */
    public Byte getTrialEnabled() {
        return (Byte) get(9);
    }

    /**
     * Setter for <code>channel.data_destination</code>. 数据流向(inner/mainland/overseas)
     */
    public void setDataDestination(String value) {
        set(10, value);
    }

    /**
     * Getter for <code>channel.data_destination</code>. 数据流向(inner/mainland/overseas)
     */
    public String getDataDestination() {
        return (String) get(10);
    }

    /**
     * Setter for <code>channel.priority</code>. 优先级(high/normal/low)
     */
    public void setPriority(String value) {
        set(11, value);
    }

    /**
     * Getter for <code>channel.priority</code>. 优先级(high/normal/low)
     */
    public String getPriority() {
        return (String) get(11);
    }

    /**
     * Setter for <code>channel.protocol</code>. 协议
     */
    public void setProtocol(String value) {
        set(12, value);
    }

    /**
     * Getter for <code>channel.protocol</code>. 协议
     */
    public String getProtocol() {
        return (String) get(12);
    }

    /**
     * Setter for <code>channel.supplier</code>. 服务商
     */
    public void setSupplier(String value) {
        set(13, value);
    }

    /**
     * Getter for <code>channel.supplier</code>. 服务商
     */
    public String getSupplier() {
        return (String) get(13);
    }

    /**
     * Setter for <code>channel.url</code>. 请求通道的url
     */
    public void setUrl(String value) {
        set(14, value);
    }

    /**
     * Getter for <code>channel.url</code>. 请求通道的url
     */
    public String getUrl() {
        return (String) get(14);
    }

    /**
     * Setter for <code>channel.channel_info</code>. 渠道信息
     */
    public void setChannelInfo(String value) {
        set(15, value);
    }

    /**
     * Getter for <code>channel.channel_info</code>. 渠道信息
     */
    public String getChannelInfo() {
        return (String) get(15);
    }

    /**
     * Setter for <code>channel.price_info</code>. 单价
     */
    public void setPriceInfo(String value) {
        set(16, value);
    }

    /**
     * Getter for <code>channel.price_info</code>. 单价
     */
    public String getPriceInfo() {
        return (String) get(16);
    }

    /**
     * Setter for <code>channel.cuid</code>. 创建人id
     */
    public void setCuid(Long value) {
        set(17, value);
    }

    /**
     * Getter for <code>channel.cuid</code>. 创建人id
     */
    public Long getCuid() {
        return (Long) get(17);
    }

    /**
     * Setter for <code>channel.cu_name</code>. 创建人姓名
     */
    public void setCuName(String value) {
        set(18, value);
    }

    /**
     * Getter for <code>channel.cu_name</code>. 创建人姓名
     */
    public String getCuName() {
        return (String) get(18);
    }

    /**
     * Setter for <code>channel.muid</code>. 编辑人id
     */
    public void setMuid(Long value) {
        set(19, value);
    }

    /**
     * Getter for <code>channel.muid</code>. 编辑人id
     */
    public Long getMuid() {
        return (Long) get(19);
    }

    /**
     * Setter for <code>channel.mu_name</code>. 编辑人姓名
     */
    public void setMuName(String value) {
        set(20, value);
    }

    /**
     * Getter for <code>channel.mu_name</code>. 编辑人姓名
     */
    public String getMuName() {
        return (String) get(20);
    }

    /**
     * Setter for <code>channel.ctime</code>.
     */
    public void setCtime(LocalDateTime value) {
        set(21, value);
    }

    /**
     * Getter for <code>channel.ctime</code>.
     */
    public LocalDateTime getCtime() {
        return (LocalDateTime) get(21);
    }

    /**
     * Setter for <code>channel.mtime</code>.
     */
    public void setMtime(LocalDateTime value) {
        set(22, value);
    }

    /**
     * Getter for <code>channel.mtime</code>.
     */
    public LocalDateTime getMtime() {
        return (LocalDateTime) get(22);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached ChannelRecord
     */
    public ChannelRecord() {
        super(Channel.CHANNEL);
    }

    /**
     * Create a detached, initialised ChannelRecord
     */
    public ChannelRecord(Long id, String entityType, String entityCode, String channelCode, String status, String ownerType, String ownerCode, String ownerName, String visibility, Byte trialEnabled, String dataDestination, String priority, String protocol, String supplier, String url, String channelInfo, String priceInfo, Long cuid, String cuName, Long muid, String muName, LocalDateTime ctime, LocalDateTime mtime) {
        super(Channel.CHANNEL);

        setId(id);
        setEntityType(entityType);
        setEntityCode(entityCode);
        setChannelCode(channelCode);
        setStatus(status);
        setOwnerType(ownerType);
        setOwnerCode(ownerCode);
        setOwnerName(ownerName);
        setVisibility(visibility);
        setTrialEnabled(trialEnabled);
        setDataDestination(dataDestination);
        setPriority(priority);
        setProtocol(protocol);
        setSupplier(supplier);
        setUrl(url);
        setChannelInfo(channelInfo);
        setPriceInfo(priceInfo);
        setCuid(cuid);
        setCuName(cuName);
        setMuid(muid);
        setMuName(muName);
        setCtime(ctime);
        setMtime(mtime);
    }
}

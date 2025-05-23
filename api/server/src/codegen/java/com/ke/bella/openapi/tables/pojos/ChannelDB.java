/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables.pojos;


import com.ke.bella.openapi.db.repo.Operator;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * 通道
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ChannelDB implements Operator, Serializable {

    private static final long serialVersionUID = 1L;

    private Long          id;
    private String        entityType;
    private String        entityCode;
    private String        channelCode;
    private String        status;
    private String        ownerType;
    private String        ownerCode;
    private String        ownerName;
    private String        visibility;
    private Byte          trialEnabled;
    private String        dataDestination;
    private String        priority;
    private String        protocol;
    private String        supplier;
    private String        url;
    private String        channelInfo;
    private String        priceInfo;
    private Long          cuid;
    private String        cuName;
    private Long          muid;
    private String        muName;
    private LocalDateTime ctime;
    private LocalDateTime mtime;

    public ChannelDB() {}

    public ChannelDB(ChannelDB value) {
        this.id = value.id;
        this.entityType = value.entityType;
        this.entityCode = value.entityCode;
        this.channelCode = value.channelCode;
        this.status = value.status;
        this.ownerType = value.ownerType;
        this.ownerCode = value.ownerCode;
        this.ownerName = value.ownerName;
        this.visibility = value.visibility;
        this.trialEnabled = value.trialEnabled;
        this.dataDestination = value.dataDestination;
        this.priority = value.priority;
        this.protocol = value.protocol;
        this.supplier = value.supplier;
        this.url = value.url;
        this.channelInfo = value.channelInfo;
        this.priceInfo = value.priceInfo;
        this.cuid = value.cuid;
        this.cuName = value.cuName;
        this.muid = value.muid;
        this.muName = value.muName;
        this.ctime = value.ctime;
        this.mtime = value.mtime;
    }

    public ChannelDB(
        Long          id,
        String        entityType,
        String        entityCode,
        String        channelCode,
        String        status,
        String        ownerType,
        String        ownerCode,
        String        ownerName,
        String        visibility,
        Byte          trialEnabled,
        String        dataDestination,
        String        priority,
        String        protocol,
        String        supplier,
        String        url,
        String        channelInfo,
        String        priceInfo,
        Long          cuid,
        String        cuName,
        Long          muid,
        String        muName,
        LocalDateTime ctime,
        LocalDateTime mtime
    ) {
        this.id = id;
        this.entityType = entityType;
        this.entityCode = entityCode;
        this.channelCode = channelCode;
        this.status = status;
        this.ownerType = ownerType;
        this.ownerCode = ownerCode;
        this.ownerName = ownerName;
        this.visibility = visibility;
        this.trialEnabled = trialEnabled;
        this.dataDestination = dataDestination;
        this.priority = priority;
        this.protocol = protocol;
        this.supplier = supplier;
        this.url = url;
        this.channelInfo = channelInfo;
        this.priceInfo = priceInfo;
        this.cuid = cuid;
        this.cuName = cuName;
        this.muid = muid;
        this.muName = muName;
        this.ctime = ctime;
        this.mtime = mtime;
    }

    /**
     * Getter for <code>channel.id</code>. 主键ID
     */
    public Long getId() {
        return this.id;
    }

    /**
     * Setter for <code>channel.id</code>. 主键ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Getter for <code>channel.entity_type</code>. 实体类型（endpoint/model）
     */
    public String getEntityType() {
        return this.entityType;
    }

    /**
     * Setter for <code>channel.entity_type</code>. 实体类型（endpoint/model）
     */
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    /**
     * Getter for <code>channel.entity_code</code>. 实体编码
     */
    public String getEntityCode() {
        return this.entityCode;
    }

    /**
     * Setter for <code>channel.entity_code</code>. 实体编码
     */
    public void setEntityCode(String entityCode) {
        this.entityCode = entityCode;
    }

    /**
     * Getter for <code>channel.channel_code</code>. 渠道编码
     */
    public String getChannelCode() {
        return this.channelCode;
    }

    /**
     * Setter for <code>channel.channel_code</code>. 渠道编码
     */
    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    /**
     * Getter for <code>channel.status</code>. 状态状态(active/inactive)
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Setter for <code>channel.status</code>. 状态状态(active/inactive)
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Getter for <code>channel.owner_type</code>. 所有者类型（组织/个人）
     */
    public String getOwnerType() {
        return this.ownerType;
    }

    /**
     * Setter for <code>channel.owner_type</code>. 所有者类型（组织/个人）
     */
    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    /**
     * Getter for <code>channel.owner_code</code>. 所有者系统号
     */
    public String getOwnerCode() {
        return this.ownerCode;
    }

    /**
     * Setter for <code>channel.owner_code</code>. 所有者系统号
     */
    public void setOwnerCode(String ownerCode) {
        this.ownerCode = ownerCode;
    }

    /**
     * Getter for <code>channel.owner_name</code>. 所有者名称
     */
    public String getOwnerName() {
        return this.ownerName;
    }

    /**
     * Setter for <code>channel.owner_name</code>. 所有者名称
     */
    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    /**
     * Getter for <code>channel.visibility</code>. 是否公开(private/public)
     */
    public String getVisibility() {
        return this.visibility;
    }

    /**
     * Setter for <code>channel.visibility</code>. 是否公开(private/public)
     */
    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    /**
     * Getter for <code>channel.trial_enabled</code>. 是否支持试用
     */
    public Byte getTrialEnabled() {
        return this.trialEnabled;
    }

    /**
     * Setter for <code>channel.trial_enabled</code>. 是否支持试用
     */
    public void setTrialEnabled(Byte trialEnabled) {
        this.trialEnabled = trialEnabled;
    }

    /**
     * Getter for <code>channel.data_destination</code>. 数据流向(inner/mainland/overseas)
     */
    public String getDataDestination() {
        return this.dataDestination;
    }

    /**
     * Setter for <code>channel.data_destination</code>. 数据流向(inner/mainland/overseas)
     */
    public void setDataDestination(String dataDestination) {
        this.dataDestination = dataDestination;
    }

    /**
     * Getter for <code>channel.priority</code>. 优先级(high/normal/low)
     */
    public String getPriority() {
        return this.priority;
    }

    /**
     * Setter for <code>channel.priority</code>. 优先级(high/normal/low)
     */
    public void setPriority(String priority) {
        this.priority = priority;
    }

    /**
     * Getter for <code>channel.protocol</code>. 协议
     */
    public String getProtocol() {
        return this.protocol;
    }

    /**
     * Setter for <code>channel.protocol</code>. 协议
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Getter for <code>channel.supplier</code>. 服务商
     */
    public String getSupplier() {
        return this.supplier;
    }

    /**
     * Setter for <code>channel.supplier</code>. 服务商
     */
    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    /**
     * Getter for <code>channel.url</code>. 请求通道的url
     */
    public String getUrl() {
        return this.url;
    }

    /**
     * Setter for <code>channel.url</code>. 请求通道的url
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Getter for <code>channel.channel_info</code>. 渠道信息
     */
    public String getChannelInfo() {
        return this.channelInfo;
    }

    /**
     * Setter for <code>channel.channel_info</code>. 渠道信息
     */
    public void setChannelInfo(String channelInfo) {
        this.channelInfo = channelInfo;
    }

    /**
     * Getter for <code>channel.price_info</code>. 单价
     */
    public String getPriceInfo() {
        return this.priceInfo;
    }

    /**
     * Setter for <code>channel.price_info</code>. 单价
     */
    public void setPriceInfo(String priceInfo) {
        this.priceInfo = priceInfo;
    }

    /**
     * Getter for <code>channel.cuid</code>. 创建人id
     */
    public Long getCuid() {
        return this.cuid;
    }

    /**
     * Setter for <code>channel.cuid</code>. 创建人id
     */
    public void setCuid(Long cuid) {
        this.cuid = cuid;
    }

    /**
     * Getter for <code>channel.cu_name</code>. 创建人姓名
     */
    public String getCuName() {
        return this.cuName;
    }

    /**
     * Setter for <code>channel.cu_name</code>. 创建人姓名
     */
    public void setCuName(String cuName) {
        this.cuName = cuName;
    }

    /**
     * Getter for <code>channel.muid</code>. 编辑人id
     */
    public Long getMuid() {
        return this.muid;
    }

    /**
     * Setter for <code>channel.muid</code>. 编辑人id
     */
    public void setMuid(Long muid) {
        this.muid = muid;
    }

    /**
     * Getter for <code>channel.mu_name</code>. 编辑人姓名
     */
    public String getMuName() {
        return this.muName;
    }

    /**
     * Setter for <code>channel.mu_name</code>. 编辑人姓名
     */
    public void setMuName(String muName) {
        this.muName = muName;
    }

    /**
     * Getter for <code>channel.ctime</code>.
     */
    public LocalDateTime getCtime() {
        return this.ctime;
    }

    /**
     * Setter for <code>channel.ctime</code>.
     */
    public void setCtime(LocalDateTime ctime) {
        this.ctime = ctime;
    }

    /**
     * Getter for <code>channel.mtime</code>.
     */
    public LocalDateTime getMtime() {
        return this.mtime;
    }

    /**
     * Setter for <code>channel.mtime</code>.
     */
    public void setMtime(LocalDateTime mtime) {
        this.mtime = mtime;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ChannelDB (");

        sb.append(id);
        sb.append(", ").append(entityType);
        sb.append(", ").append(entityCode);
        sb.append(", ").append(channelCode);
        sb.append(", ").append(status);
        sb.append(", ").append(ownerType);
        sb.append(", ").append(ownerCode);
        sb.append(", ").append(ownerName);
        sb.append(", ").append(visibility);
        sb.append(", ").append(trialEnabled);
        sb.append(", ").append(dataDestination);
        sb.append(", ").append(priority);
        sb.append(", ").append(protocol);
        sb.append(", ").append(supplier);
        sb.append(", ").append(url);
        sb.append(", ").append(channelInfo);
        sb.append(", ").append(priceInfo);
        sb.append(", ").append(cuid);
        sb.append(", ").append(cuName);
        sb.append(", ").append(muid);
        sb.append(", ").append(muName);
        sb.append(", ").append(ctime);
        sb.append(", ").append(mtime);

        sb.append(")");
        return sb.toString();
    }
}

package com.ke.bella.openapi.tables.pojos;

import java.io.Serializable;
import java.time.LocalDateTime;

public class AkChannelRelDB implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String akCode;
    private String channelCode;
    private Long cuid;
    private String cuName;
    private Long muid;
    private String muName;
    private LocalDateTime ctime;
    private LocalDateTime mtime;

    public AkChannelRelDB() {}

    public AkChannelRelDB(Long id, String akCode, String channelCode, Long cuid, String cuName,
            Long muid, String muName, LocalDateTime ctime, LocalDateTime mtime) {
        this.id = id;
        this.akCode = akCode;
        this.channelCode = channelCode;
        this.cuid = cuid;
        this.cuName = cuName;
        this.muid = muid;
        this.muName = muName;
        this.ctime = ctime;
        this.mtime = mtime;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAkCode() { return akCode; }
    public void setAkCode(String akCode) { this.akCode = akCode; }
    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }
    public Long getCuid() { return cuid; }
    public void setCuid(Long cuid) { this.cuid = cuid; }
    public String getCuName() { return cuName; }
    public void setCuName(String cuName) { this.cuName = cuName; }
    public Long getMuid() { return muid; }
    public void setMuid(Long muid) { this.muid = muid; }
    public String getMuName() { return muName; }
    public void setMuName(String muName) { this.muName = muName; }
    public LocalDateTime getCtime() { return ctime; }
    public void setCtime(LocalDateTime ctime) { this.ctime = ctime; }
    public LocalDateTime getMtime() { return mtime; }
    public void setMtime(LocalDateTime mtime) { this.mtime = mtime; }
}

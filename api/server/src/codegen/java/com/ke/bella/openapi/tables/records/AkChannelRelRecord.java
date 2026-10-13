package com.ke.bella.openapi.tables.records;

import com.ke.bella.openapi.tables.AkChannelRel;
import java.time.LocalDateTime;
import org.jooq.impl.UpdatableRecordImpl;

@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class AkChannelRelRecord extends UpdatableRecordImpl<AkChannelRelRecord> {
    private static final long serialVersionUID = 1L;

    public AkChannelRelRecord() {
        super(AkChannelRel.AK_CHANNEL_REL);
    }

    public Long getId() { return (Long) get(0); }
    public void setId(Long value) { set(0, value); }
    public String getAkCode() { return (String) get(1); }
    public void setAkCode(String value) { set(1, value); }
    public String getChannelCode() { return (String) get(2); }
    public void setChannelCode(String value) { set(2, value); }
    public Long getCuid() { return (Long) get(3); }
    public void setCuid(Long value) { set(3, value); }
    public String getCuName() { return (String) get(4); }
    public void setCuName(String value) { set(4, value); }
    public Long getMuid() { return (Long) get(5); }
    public void setMuid(Long value) { set(5, value); }
    public String getMuName() { return (String) get(6); }
    public void setMuName(String value) { set(6, value); }
    public LocalDateTime getCtime() { return (LocalDateTime) get(7); }
    public void setCtime(LocalDateTime value) { set(7, value); }
    public LocalDateTime getMtime() { return (LocalDateTime) get(8); }
    public void setMtime(LocalDateTime value) { set(8, value); }
}

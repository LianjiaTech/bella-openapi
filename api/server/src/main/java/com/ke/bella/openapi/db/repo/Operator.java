package com.ke.bella.openapi.db.repo;

public interface Operator extends Timed {

    Long getId();

    void setId(Long id);

    Long getCuid();

    void setCuid(Long cuid);

    String getCuName();

    void setCuName(String cuName);

    Long getMuid();

    void setMuid(Long muid);

    String getMuName();

    void setMuName(String muName);
}

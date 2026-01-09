package com.ke.bella.openapi.db.repo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public interface Timed {
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime getCtime();

    void setCtime(LocalDateTime ctime);

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime getMtime();

    void setMtime(LocalDateTime mtime);
}

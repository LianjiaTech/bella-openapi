package com.ke.bella.openapi.db.repo;

import static com.ke.bella.openapi.Tables.INSTANCE;
import static org.springframework.transaction.annotation.Isolation.READ_COMMITTED;

import java.time.LocalDateTime;

import javax.annotation.Resource;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ke.bella.openapi.tables.records.InstanceRecord;

@Component
public class InstanceRepo {
    @Resource
    private DSLContext db;

    @Transactional(isolation = READ_COMMITTED)
    public Long register(String ip, int port) {
        LocalDateTime now = LocalDateTime.now();

        db.insertInto(INSTANCE)
                .set(INSTANCE.IP, ip)
                .set(INSTANCE.PORT, port)
                .set(INSTANCE.STATUS, 1)
                .set(INSTANCE.CTIME, now)
                .set(INSTANCE.MTIME, now)
                .onDuplicateKeyUpdate()
                .set(INSTANCE.STATUS, 1)
                .set(INSTANCE.MTIME, now)
                .execute();

        InstanceRecord rec = db.selectFrom(INSTANCE)
                .where(INSTANCE.IP.eq(ip).and(INSTANCE.PORT.eq(port)))
                .fetchOne();

        return rec.getId();
    }

    public void unregister(String ip, int port) {
        InstanceRecord rec = db.selectFrom(INSTANCE)
                .where(INSTANCE.IP.eq(ip).and(INSTANCE.PORT.eq(port))).fetchOne();
        if(rec != null) {
            rec.setStatus(0);
            rec.set(INSTANCE.MTIME, LocalDateTime.now());
            rec.store();
        }
    }
}

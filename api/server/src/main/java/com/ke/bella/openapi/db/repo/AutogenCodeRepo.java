package com.ke.bella.openapi.db.repo;

import org.jooq.TableField;
import org.jooq.impl.UpdatableRecordImpl;

import java.util.UUID;

/**
 * Author: Stan Sai Date: 2024/8/8 12:26 description:
 */
public interface AutogenCodeRepo<R extends UpdatableRecordImpl<R>> {
    TableField<R, String> autoCode();

    default String prefix() {
        return "";
    }

    default void autogen(R rec) {
        rec.set(autoCode(), prefix() + UUID.randomUUID());
    }
}

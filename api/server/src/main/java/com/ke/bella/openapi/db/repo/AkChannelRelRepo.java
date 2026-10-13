package com.ke.bella.openapi.db.repo;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AkChannelRelRepo implements BaseRepo {
    private static final Table<Record> REL = DSL.table(DSL.name("ak_channel_rel"));
    private static final Field<String> AK_CODE = DSL.field(DSL.name("ak_code"), String.class);
    private static final Field<String> CHANNEL_CODE = DSL.field(DSL.name("channel_code"), String.class);

    @Resource
    private DSLContext db;

    public Set<String> listChannelCodesByAkCode(String akCode) {
        return db.select(CHANNEL_CODE).from(REL)
                .where(AK_CODE.eq(akCode))
                .fetch(CHANNEL_CODE)
                .stream()
                .collect(Collectors.toSet());
    }

    public Set<String> listRestrictedChannelCodes(Collection<String> channelCodes) {
        return db.selectDistinct(CHANNEL_CODE).from(REL)
                .where(CHANNEL_CODE.in(channelCodes))
                .fetch(CHANNEL_CODE)
                .stream()
                .collect(Collectors.toSet());
    }

    public Map<String, List<String>> listAkCodesByChannelCodes(Collection<String> channelCodes) {
        return db.select(CHANNEL_CODE, AK_CODE).from(REL)
                .where(CHANNEL_CODE.in(channelCodes))
                .orderBy(CHANNEL_CODE.asc(), AK_CODE.asc())
                .fetch()
                .stream()
                .collect(Collectors.groupingBy(
                        record -> record.get(CHANNEL_CODE),
                        LinkedHashMap::new,
                        Collectors.mapping(record -> record.get(AK_CODE), Collectors.toList())));
    }

    public void replace(String channelCode, Collection<String> akCodes) {
        db.deleteFrom(REL).where(CHANNEL_CODE.eq(channelCode)).execute();
        if(akCodes == null || akCodes.isEmpty()) {
            return;
        }

        List<Query> inserts = akCodes.stream()
                .map(akCode -> db.insertInto(REL)
                        .set(AK_CODE, akCode)
                        .set(CHANNEL_CODE, channelCode))
                .collect(Collectors.toList());
        batchExecuteQuery(db, inserts);
    }
}

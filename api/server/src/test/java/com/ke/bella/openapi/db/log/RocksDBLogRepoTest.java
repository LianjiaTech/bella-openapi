package com.ke.bella.openapi.db.log;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rocksdb.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class RocksDBLogRepoTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void computeShardIdUsesHourlyFormat() {
        RocksDBLogRepo repo = new RocksDBLogRepo(new RocksDBProperties());
        assertThat(repo.computeShardId(LocalDateTime.of(2026, 8, 4, 12, 59, 59))).isEqualTo("20260804-12");
    }

    @Test
    public void flushAlwaysClosesFlushOptions() throws Exception {
        RocksDBResourceFactory resources = mock(RocksDBResourceFactory.class);
        FlushOptions flushOptions = mock(FlushOptions.class);
        RocksDB db = mock(RocksDB.class);
        when(resources.createFlushOptions()).thenReturn(flushOptions);
        when(flushOptions.setWaitForFlush(false)).thenReturn(flushOptions);
        RocksDBLogRepo repo = repoWithHolder(resources, db);

        repo.flush();
        doThrow(new RocksDBException("flush failed")).when(db).flush(flushOptions);
        repo.flush();

        verify(flushOptions, times(2)).close();
    }

    @Test
    public void holderClosesEveryOwnedResource() {
        RocksDB db = mock(RocksDB.class);
        WriteOptions writeOptions = mock(WriteOptions.class);
        Options options = mock(Options.class);
        BloomFilter filter = mock(BloomFilter.class);
        RocksDBLogRepo.DBHolder holder = new RocksDBLogRepo.DBHolder(db, writeOptions, options, filter, "shard");

        holder.close();

        verify(db).close();
        verify(writeOptions).close();
        verify(options).close();
        verify(filter).close();
    }

    @Test
    public void failedOpenClosesCreatedResourcesAndPublishesNothing() throws Exception {
        RocksDBProperties properties = new RocksDBProperties();
        properties.setBasePath(temporaryFolder.newFolder("writer").getAbsolutePath());
        RocksDBResourceFactory resources = mock(RocksDBResourceFactory.class);
        Options options = mock(Options.class);
        WriteOptions writeOptions = mock(WriteOptions.class);
        BloomFilter filter = mock(BloomFilter.class);
        BlockBasedTableConfig table = mock(BlockBasedTableConfig.class);
        when(resources.createOptions()).thenReturn(options);
        when(resources.createWriteOptions()).thenReturn(writeOptions);
        when(resources.createBloomFilter()).thenReturn(filter);
        when(resources.createTableConfig()).thenReturn(table);
        when(resources.open(eq(options), anyString())).thenThrow(new RocksDBException("open failed"));
        RocksDBLogRepo repo = new RocksDBLogRepo(properties, resources);

        repo.init();

        assertThat(repo.getCurrentShardPath()).isNull();
        verify(writeOptions).close();
        verify(options).close();
        verify(filter).close();
    }

    private RocksDBLogRepo repoWithHolder(RocksDBResourceFactory resources, RocksDB db) throws Exception {
        RocksDBLogRepo repo = new RocksDBLogRepo(new RocksDBProperties(), resources);
        Field instanceId = RocksDBLogRepo.class.getDeclaredField("instanceId");
        instanceId.setAccessible(true);
        instanceId.set(repo, "test");
        Field field = RocksDBLogRepo.class.getDeclaredField("currentDB");
        field.setAccessible(true);
        field.set(repo, new RocksDBLogRepo.DBHolder(db, mock(WriteOptions.class), mock(Options.class), mock(BloomFilter.class), "shard"));
        return repo;
    }
}

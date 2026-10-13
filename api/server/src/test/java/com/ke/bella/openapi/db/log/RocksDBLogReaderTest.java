package com.ke.bella.openapi.db.log;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rocksdb.*;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class RocksDBLogReaderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void failedSecondaryOpenClosesCreatedResources() throws Exception {
        RocksDBResourceFactory resources = mockFactory();
        Options options = mock(Options.class);
        BloomFilter filter = mock(BloomFilter.class);
        when(resources.createOptions()).thenReturn(options);
        when(resources.createBloomFilter()).thenReturn(filter);
        when(resources.openAsSecondary(eq(options), anyString(), anyString())).thenThrow(new RocksDBException("failed"));
        RocksDBLogReader reader = reader(resources);
        createPrimary("a");

        assertThat(reader.readByShardPath("id", "a/")).isNull();

        verify(options).close();
        verify(filter).close();
    }

    @Test
    public void invalidationAndDestroyCloseHandlesSynchronously() throws Exception {
        RocksDBResourceFactory resources = mockFactory();
        RocksDB db1 = mock(RocksDB.class);
        RocksDB db2 = mock(RocksDB.class);
        when(resources.createOptions()).thenReturn(mock(Options.class), mock(Options.class));
        when(resources.createBloomFilter()).thenReturn(mock(BloomFilter.class), mock(BloomFilter.class));
        when(resources.openAsSecondary(any(), anyString(), anyString())).thenReturn(db1, db2);
        RocksDBLogReader reader = reader(resources);
        createPrimary("a"); createPrimary("b");

        reader.readByShardPath("id", "a/");
        reader.readByShardPath("id", "b/");
        reader.invalidateCache("a/");
        verify(db1).close();
        reader.destroy();
        verify(db2).close();
    }

    @Test
    public void closeWaitsForActiveRead() throws Exception {
        RocksDB db = mock(RocksDB.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(db.get(any(byte[].class))).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        });
        RocksDBLogReader.CachedDB holder = new RocksDBLogReader.CachedDB(db, mock(Options.class), mock(BloomFilter.class));
        Thread read = new Thread(() -> {
            try { holder.get(new byte[] {1}); } catch (RocksDBException ignored) { }
        });
        Thread close = new Thread(holder::close);

        read.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        close.start();
        Thread.sleep(100);
        verify(db, never()).close();
        release.countDown();
        read.join(5000);
        close.join(5000);
        verify(db).close();
    }

    @Test
    public void invalidationWaitsForSecondaryOpenToFinish() throws Exception {
        RocksDBResourceFactory resources = mockFactory();
        RocksDB db = mock(RocksDB.class);
        CountDownLatch opening = new CountDownLatch(1);
        CountDownLatch releaseOpen = new CountDownLatch(1);
        CountDownLatch invalidated = new CountDownLatch(1);
        when(resources.createOptions()).thenReturn(mock(Options.class));
        when(resources.createBloomFilter()).thenReturn(mock(BloomFilter.class));
        when(resources.openAsSecondary(any(), anyString(), anyString())).thenAnswer(invocation -> {
            opening.countDown();
            releaseOpen.await(5, TimeUnit.SECONDS);
            return db;
        });
        RocksDBLogReader reader = reader(resources);
        createPrimary("a");
        Thread read = new Thread(() -> reader.readByShardPath("id", "a/"));
        Thread invalidate = new Thread(() -> {
            reader.invalidateCache("a/");
            invalidated.countDown();
        });

        read.start();
        assertThat(opening.await(5, TimeUnit.SECONDS)).isTrue();
        invalidate.start();
        assertThat(invalidated.await(100, TimeUnit.MILLISECONDS)).isFalse();
        verify(db, never()).close();

        releaseOpen.countDown();
        read.join(5000);
        invalidate.join(5000);
        assertThat(invalidated.getCount()).isZero();
        verify(db).close();
    }

    private RocksDBResourceFactory mockFactory() {
        RocksDBResourceFactory resources = mock(RocksDBResourceFactory.class);
        when(resources.createTableConfig()).thenAnswer(ignored -> mock(BlockBasedTableConfig.class));
        return resources;
    }

    private RocksDBLogReader reader(RocksDBResourceFactory resources) {
        RocksDBProperties properties = new RocksDBProperties();
        properties.setBasePath(temporaryFolder.getRoot().getAbsolutePath());
        return new RocksDBLogReader(properties, resources);
    }

    private File createPrimary(String name) {
        File directory = new File(temporaryFolder.getRoot(), name);
        assertThat(directory.mkdir()).isTrue();
        return directory;
    }
}

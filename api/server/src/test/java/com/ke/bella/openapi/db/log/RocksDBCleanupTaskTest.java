package com.ke.bella.openapi.db.log;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class RocksDBCleanupTaskTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    private RocksDBLogReader logReader;

    private RocksDBProperties properties;
    private RocksDBCleanupTask cleanupTask;
    private File baseDirectory;

    @Before
    public void setUp() throws Exception {
        properties = new RocksDBProperties();
        baseDirectory = temporaryFolder.newFolder("rocksdb");
        properties.setBasePath(baseDirectory.getAbsolutePath());
        cleanupTask = new RocksDBCleanupTask(properties, logReader);
    }

    @Test
    public void cleanupDeletesShardEndingAtCutoffAfterInvalidatingCache() throws Exception {
        properties.setRetentionDays(0);
        properties.setRetentionHours(6);
        File expiredShard = createDirectory("20260804-05");
        File secondaryDirectory = createDirectory("20260804-05_secondary_reader");
        File retainedShard = createDirectory("20260804-06");
        doAnswer(invocation -> {
            assertThat(expiredShard).exists();
            return null;
        }).when(logReader).invalidateCache("20260804-05/");

        cleanupTask.cleanup(LocalDateTime.of(2026, 8, 4, 12, 0));

        assertThat(expiredShard).doesNotExist();
        assertThat(secondaryDirectory).doesNotExist();
        assertThat(retainedShard).exists();
        verify(logReader).invalidateCache("20260804-05/");
        verify(logReader, never()).invalidateCache("20260804-06/");
    }

    @Test
    public void cleanupSkipsInvalidAndNonShardDirectories() throws Exception {
        properties.setRetentionHours(6);
        File invalidDate = createDirectory("20260230-05");
        File nonShard = createDirectory("not-a-shard");
        File unexpectedFormat = createDirectory("20260804");

        cleanupTask.cleanup(LocalDateTime.of(2026, 8, 4, 12, 0));

        assertThat(invalidDate).exists();
        assertThat(nonShard).exists();
        assertThat(unexpectedFormat).exists();
        verify(logReader, never()).invalidateCache(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void cleanupFallsBackToRetentionDaysWhenHoursAreNotConfigured() throws Exception {
        properties.setRetentionDays(1);
        File expiredShard = createDirectory("20260803-11");
        File retainedShard = createDirectory("20260803-12");

        cleanupTask.cleanup(LocalDateTime.of(2026, 8, 4, 12, 0));

        assertThat(expiredShard).doesNotExist();
        assertThat(retainedShard).exists();
        verify(logReader).invalidateCache("20260803-11/");
        verify(logReader, never()).invalidateCache("20260803-12/");
    }

    private File createDirectory(String name) throws Exception {
        File directory = new File(baseDirectory, name);
        assertThat(directory.mkdirs()).isTrue();
        return directory;
    }
}

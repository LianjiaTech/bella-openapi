package com.ke.bella.openapi.db.repo;

import static com.ke.bella.openapi.Tables.VIDEO_JOB;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.SelectConditionStep;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ke.bella.openapi.db.IDGenerator;
import com.ke.bella.openapi.db.VideoIdGenerator;
import com.ke.bella.openapi.protocol.video.VideoJob.Status;
import com.ke.bella.openapi.tables.pojos.VideoJobDB;
import com.ke.bella.openapi.tables.records.VideoJobRecord;

@Component
public class VideoRepo implements BaseRepo {
    @Resource
    private DSLContext db;

    public VideoRepo(DSLContext db) {
        this.db = db;
    }

    private String getShardingKeyByVideoId(String videoId) {
        int shardingIdx = VideoIdGenerator.getShardingIdx(videoId);
        return formatShardingKey(shardingIdx);
    }

    private String getShardingKeyBySpaceCode(String spaceCode) {
        int hashCode = IDGenerator.hashCode(spaceCode);
        int shardingIdx = Math.abs(hashCode) % 16;
        return formatShardingKey(shardingIdx);
    }

    private String formatShardingKey(int shardingIdx) {
        return String.format("%02d", shardingIdx);
    }

    private DSLContext db(String shardingKey) {
        return DSLContextHolder.get(shardingKey, db);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addVideoJob(VideoJobDB videoJobDB) {
        String shardingKey = getShardingKeyByVideoId(videoJobDB.getVideoId());

        VideoJobRecord rec = VIDEO_JOB.newRecord();
        rec.from(videoJobDB);
        fillCreatorInfo(rec);

        int insertedNum = db(shardingKey).insertInto(VIDEO_JOB)
                .set(rec)
                .execute();

        if(insertedNum != 1) {
            throw new IllegalStateException("insert video_job failed, videoId: " + videoJobDB.getVideoId());
        }
    }

    public VideoJobDB queryVideoJob(String videoId) {
        String shardingKey = getShardingKeyByVideoId(videoId);
        return db(shardingKey).selectFrom(VIDEO_JOB)
                .where(VIDEO_JOB.VIDEO_ID.eq(videoId))
                .and(VIDEO_JOB.STATUS.ne(Status.deleted.name()))
                .fetchOneInto(VideoJobDB.class);
    }

    public List<VideoJobDB> listVideoJobs(String spaceCode, String after, Integer limit, String order) {
        String shardingKey = getShardingKeyBySpaceCode(spaceCode);

        SelectConditionStep<VideoJobRecord> query = db(shardingKey).selectFrom(VIDEO_JOB)
                .where(VIDEO_JOB.STATUS.ne(Status.deleted.name()))
                .and(VIDEO_JOB.SPACE_CODE.eq(spaceCode));

        if(after != null) {
            query = query.and(VIDEO_JOB.VIDEO_ID.gt(after));
        }

        if("asc".equalsIgnoreCase(order)) {
            return query.orderBy(VIDEO_JOB.CTIME.asc())
                    .limit(limit)
                    .fetchInto(VideoJobDB.class);
        } else {
            return query.orderBy(VIDEO_JOB.CTIME.desc())
                    .limit(limit)
                    .fetchInto(VideoJobDB.class);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateVideoJobStatus(String videoId, String status) {
        VideoJobDB update = new VideoJobDB();
        update.setVideoId(videoId);
        update.setStatus(status);
        batchUpdateVideoJobs(java.util.Collections.singletonList(update));
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateVideoJobs(List<VideoJobDB> updates) {
        if(updates == null || updates.isEmpty()) {
            return;
        }

        Map<String, List<VideoJobDB>> groupedBySharding = new HashMap<>();

        for (VideoJobDB job : updates) {
            String shardingKey = getShardingKeyByVideoId(job.getVideoId());
            groupedBySharding.computeIfAbsent(shardingKey, k -> new ArrayList<>()).add(job);
        }

        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, List<VideoJobDB>> entry : groupedBySharding.entrySet()) {
            String shardingKey = entry.getKey();
            List<VideoJobDB> jobs = entry.getValue();

            List<Query> queries = new ArrayList<>(jobs.size());

            for (VideoJobDB job : jobs) {
                Map<Field<?>, Object> updateMap = new HashMap<>();

                if(job.getStatus() != null) {
                    updateMap.put(VIDEO_JOB.STATUS, job.getStatus());
                }
                if(job.getChannelCode() != null) {
                    updateMap.put(VIDEO_JOB.CHANNEL_CODE, job.getChannelCode());
                }
                if(job.getChannelVideoId() != null) {
                    updateMap.put(VIDEO_JOB.CHANNEL_VIDEO_ID, job.getChannelVideoId());
                }
                if(job.getBoundFileId() != null) {
                    updateMap.put(VIDEO_JOB.BOUND_FILE_ID, job.getBoundFileId());
                }
                if(job.getSeconds() != null) {
                    updateMap.put(VIDEO_JOB.SECONDS, job.getSeconds());
                }
                if(job.getSize() != null) {
                    updateMap.put(VIDEO_JOB.SIZE, job.getSize());
                }
                if(job.getProgress() != null) {
                    updateMap.put(VIDEO_JOB.PROGRESS, job.getProgress());
                }
                if(job.getCompletedAt() != null) {
                    updateMap.put(VIDEO_JOB.COMPLETED_AT, job.getCompletedAt());
                }
                if(job.getError() != null) {
                    updateMap.put(VIDEO_JOB.ERROR, job.getError());
                }

                updateMap.put(VIDEO_JOB.MTIME, now);

                Query query = db(shardingKey).update(VIDEO_JOB)
                        .set(updateMap)
                        .where(VIDEO_JOB.VIDEO_ID.eq(job.getVideoId()));

                queries.add(query);
            }

            db(shardingKey).batch(queries).execute();
        }
    }

    public boolean casUpdateToSubmitting(
            String videoId,
            String channelCode) {
        String shardingKey = getShardingKeyByVideoId(videoId);

        int updated = db(shardingKey).update(VIDEO_JOB)
                .set(VIDEO_JOB.STATUS, Status.submitting.name())
                .set(VIDEO_JOB.CHANNEL_CODE, channelCode)
                .set(VIDEO_JOB.MTIME, LocalDateTime.now())
                .where(VIDEO_JOB.VIDEO_ID.eq(videoId))
                .and(VIDEO_JOB.STATUS.eq(Status.queued.name()))
                .execute();

        return updated == 1;
    }

    @Deprecated
    public boolean claimVideoJobForSubmitting(
            String videoId,
            String channelCode) {
        return casUpdateToSubmitting(videoId, channelCode);
    }

    public boolean casUpdateToProcessing(
            String videoId,
            String channelVideoId) {
        String shardingKey = getShardingKeyByVideoId(videoId);

        int updated = db(shardingKey).update(VIDEO_JOB)
                .set(VIDEO_JOB.STATUS, Status.processing.name())
                .set(VIDEO_JOB.CHANNEL_VIDEO_ID, channelVideoId)
                .set(VIDEO_JOB.MTIME, LocalDateTime.now())
                .where(VIDEO_JOB.VIDEO_ID.eq(videoId))
                .and(VIDEO_JOB.STATUS.eq(Status.submitting.name()))
                .execute();

        return updated == 1;
    }

    @Deprecated
    public boolean updateVideoJobToProcessing(
            String videoId,
            String channelVideoId) {
        return casUpdateToProcessing(videoId, channelVideoId);
    }

}

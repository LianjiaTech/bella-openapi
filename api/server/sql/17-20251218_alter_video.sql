-- 在 video_job 表的 status 字段后添加 file_id 字段
ALTER TABLE video_job
    ADD COLUMN bound_file_id           varchar(256) DEFAULT '' NOT NULL COMMENT '绑定的文件ID（用于file api转储检索）' AFTER status,
    ADD COLUMN input_reference_file_id varchar(256) DEFAULT '' NOT NULL COMMENT '输入参考文件ID（用户上传的参考视频/图片）' AFTER prompt,
    MODIFY COLUMN status varchar(32) DEFAULT 'queued' NOT NULL
        COMMENT '任务状态(queued/submitting/processing/completed/failed/cancelled)';

-- 1. 插入 /v1/videos endpoint
INSERT INTO endpoint (endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid, cu_name, muid, mu_name)
VALUES ('/v1/videos', 'ep-videos-001', N'视频生成', '0', 'system', 'active', 0, 'system', 0, 'system');

-- 2. 创建视频分类（如果不存在）
INSERT INTO category (category_code, category_name, parent_code, status, cuid, cu_name, muid, mu_name)
VALUES ('0004', N'视频类', '', 'active', 0, 'system', 0, 'system')
ON DUPLICATE KEY UPDATE category_name=N'视频类';

-- 3. 关联endpoint到分类
INSERT INTO endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
VALUES ('/v1/videos', '0004', 0, 'system', 0, 'system');

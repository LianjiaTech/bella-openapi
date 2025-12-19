SET NAMES utf8mb4;

create table video_job
(
    id                    bigint(20)                             not null auto_increment comment '主键ID',
    video_id              varchar(128) default ''                not null comment '视频ID/任务ID',
    space_code            varchar(64)  default ''                not null comment '空间编码',
    ak_code               varchar(64)  default ''                not null comment 'API Key编码',
    model                 varchar(64)  default ''                not null comment '模型名称',
    progress              int(10)      default 0                 not null comment '进度百分比',
    prompt                longtext     default null comment '提示词',
    seconds               bigint(20)   default 0                 not null comment '时长',
    size                  varchar(32)  default ''                not null comment '视频尺寸',
    remixed_from_video_id varchar(128) default ''                not null comment '源视频ID（remix任务）',
    completed_at          timestamp    default null comment '视频任务完成时间',
    expires_at            timestamp    default null comment '视频任务下载过期时间',
    status                varchar(32)  default 'queued'          not null comment '任务状态(queued/processing/completed/failed/cancelled)',

    callback_url          varchar(512) default ''                not null comment '回调URL',
    callback_status       tinyint(4)   default 0                 not null comment '回调状态(-1：回调失败；0：未回调；1：回调成功)',

    channel_code          varchar(64)  default ''                not null comment '渠道编码',
    channel_video_id      varchar(512) default ''                not null comment '渠道视频ID',
    error                 longtext     default null comment '错误详情（JSON格式，包含code/message等）',

    cuid                  bigint(20)   default 0                 not null comment '创建人ID',
    cu_name               varchar(64)  default ''                not null comment '创建人姓名',
    muid                  bigint(20)   default 0                 not null comment '修改人ID',
    mu_name               varchar(64)  default ''                not null comment '修改人姓名',
    ctime                 timestamp    default CURRENT_TIMESTAMP not null,
    mtime                 timestamp    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key (id),
    unique key `uniq_idx_job_id` (video_id),
    key `idx_space_code` (`space_code`),
    key `idx_ak_code` (`ak_code`),
    key `idx_model` (`model`),
    key `idx_channel_video_id` (`channel_code`, `channel_video_id`),
    key `idx_status` (`status`)
) engine = InnoDB
  default charset = utf8mb4 comment ='视频任务表';

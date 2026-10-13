SET NAMES utf8mb4;

CREATE TABLE `ak_channel_rel` (
    `id`           bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `ak_code`      varchar(64) NOT NULL DEFAULT '' COMMENT 'AK编码',
    `channel_code` varchar(64) NOT NULL DEFAULT '' COMMENT '渠道编码',
    `cuid`         bigint(20) NOT NULL DEFAULT 0 COMMENT '创建人id',
    `cu_name`      varchar(16) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    `muid`         bigint(20) NOT NULL DEFAULT 0 COMMENT '编辑人id',
    `mu_name`      varchar(16) NOT NULL DEFAULT '' COMMENT '编辑人姓名',
    `ctime`        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_ak_channel_code` (`ak_code`, `channel_code`),
    KEY `idx_channel_code_ak_code` (`channel_code`, `ak_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AK与专用渠道关联';

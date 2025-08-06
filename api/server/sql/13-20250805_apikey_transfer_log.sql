SET NAMES utf8mb4;

-- API Key所有权转移审计日志表
CREATE TABLE `apikey_transfer_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `ak_code` VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'API Key编码',
    `from_owner_type` VARCHAR(16) NOT NULL DEFAULT '' COMMENT '原所有者类型',
    `from_owner_code` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '原所有者编码',
    `from_owner_name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '原所有者姓名',
    `to_owner_type` VARCHAR(16) NOT NULL DEFAULT '' COMMENT '新所有者类型',
    `to_owner_code` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '新所有者编码',
    `to_owner_name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '新所有者姓名',
    `transfer_reason` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '转移原因',
    `status` VARCHAR(16) NOT NULL DEFAULT 'completed' COMMENT '转移状态(pending/completed/failed)',
    `operator_uid` BIGINT NOT NULL DEFAULT 0 COMMENT '操作人用户ID',
    `operator_name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '操作人姓名',
    `ctime` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `mtime` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ak_code` (`ak_code`),
    KEY `idx_from_owner` (`from_owner_type`, `from_owner_code`),
    KEY `idx_to_owner` (`to_owner_type`, `to_owner_code`),
    KEY `idx_ctime` (`ctime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API Key所有权转移审计日志表';
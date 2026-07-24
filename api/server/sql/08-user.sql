SET NAMES utf8mb4;
CREATE TABLE `user` (
    `id` BIGINT NOT NULL auto_increment COMMENT '用户ID',
    `user_name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '用户名',
    `email` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '邮箱',
    `source` VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户来源',
    `source_id` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源ID',
    `manager_ak` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '管理员ak-code',
    `optional_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '扩展信息',
    `ctime`  datetime  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `mtime`  datetime  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后一次更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source_source_id` (`source`, `source_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

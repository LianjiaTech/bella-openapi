SET NAMES utf8mb4;
ALTER TABLE `channel`
    MODIFY COLUMN `channel_info` varchar (4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '{}' COMMENT '渠道信息';

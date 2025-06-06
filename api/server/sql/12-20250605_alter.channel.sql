SET NAMES utf8mb4;
ALTER TABLE `channel` MODIFY COLUMN `channel_info` varchar (4096) NOT NULL DEFAULT '{}' COMMENT '渠道信息';

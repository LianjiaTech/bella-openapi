SET NAMES utf8mb4;

ALTER TABLE `channel` MODIFY COLUMN `price_info` varchar(4096) DEFAULT '{}' NOT NULL COMMENT '单价';

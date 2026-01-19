SET NAMES utf8mb4;

ALTER TABLE `channel` MODIFY COLUMN `price_info` varchar (8192) DEFAULT '{}' NOT NULL COMMENT '单价';

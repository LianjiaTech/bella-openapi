SET NAMES utf8mb4;
ALTER TABLE `channel` ADD COLUMN `queue_mode` tinyint(4) NOT NULL DEFAULT 0 COMMENT '队列模式(0:无队列;1:pull模式;2:route模式;3:pull+route模式)' AFTER `price_info`;
ALTER TABLE `channel` ADD COLUMN `queue_name` varchar(255) NOT NULL DEFAULT '' COMMENT '队列名称' AFTER `queue_mode`;

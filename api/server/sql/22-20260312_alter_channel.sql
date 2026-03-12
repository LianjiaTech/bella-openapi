SET NAMES utf8mb4;
ALTER TABLE `channel` ADD COLUMN `worker_mode` tinyint(4) NOT NULL DEFAULT 0 COMMENT 'worker模式(0:非worker;1:单条worker;2:批量worker)' AFTER `price_info`;

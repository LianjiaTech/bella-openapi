SET NAMES utf8mb4;
ALTER TABLE `channel` ADD INDEX `idx_queue_name` (`queue_name`);

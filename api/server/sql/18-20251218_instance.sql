-- ----------------------------
-- Table structure for instance
-- ----------------------------
CREATE TABLE `instance`
(
    `id`     bigint unsigned NOT NULL AUTO_INCREMENT,
    `ip`     varchar(64)     NOT NULL DEFAULT '',
    `port`   int             NOT NULL DEFAULT 0,
    `status` int             NOT NULL DEFAULT 0,
    `ctime`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_ip_port` (`ip`, `port`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4;

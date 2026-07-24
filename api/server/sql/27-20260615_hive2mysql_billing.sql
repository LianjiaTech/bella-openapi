SET NAMES utf8mb4;

CREATE TABLE `hive2mysql_billing_daily`
(
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `pt`           VARCHAR(64) NOT NULL DEFAULT '' COMMENT '时间',
    `endpoint`     VARCHAR(64) NOT NULL DEFAULT '' COMMENT '能力点',
    `model`        VARCHAR(64) NOT NULL DEFAULT '' COMMENT '模型名称',
    `account_type` VARCHAR(16) NOT NULL DEFAULT '' COMMENT '账户类型',
    `account_code` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '账户编码',
    `ak_code`      VARCHAR(64)          DEFAULT NULL COMMENT 'ak编码',
    `amount`       DECIMAL(20, 4) NOT NULL DEFAULT 0.0000,
    `ctime`        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_account_type_code` (`account_type`, `account_code`),
    KEY `idx_ak_code_pt` (`ak_code`, `pt`),
    KEY `idx_ak_code_endpoint_model_pt` (`ak_code`, `endpoint`, `model`, `pt`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='每日账单';

CREATE TABLE `hive2mysql_billing_month`
(
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `pt`           VARCHAR(64) NOT NULL DEFAULT '' COMMENT '时间',
    `endpoint`     VARCHAR(64) NOT NULL DEFAULT '' COMMENT '能力点',
    `model`        VARCHAR(64) NOT NULL DEFAULT '' COMMENT '模型名称',
    `account_type` VARCHAR(16) NOT NULL DEFAULT '' COMMENT '账户类型',
    `account_code` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '账户编码',
    `ak_code`      VARCHAR(64)          DEFAULT NULL COMMENT 'ak编码',
    `amount`       DECIMAL(20, 4) NOT NULL DEFAULT 0.0000,
    `ctime`        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_account_type_code` (`account_type`, `account_code`),
    KEY `idx_ak_code_pt` (`ak_code`, `pt`),
    KEY `idx_ak_code_endpoint_model_pt` (`ak_code`, `endpoint`, `model`, `pt`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='每月账单';

CREATE TABLE `hive2mysql_billing_per_hour`
(
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `pt`           VARCHAR(64) NOT NULL DEFAULT '' COMMENT '时间',
    `endpoint`     VARCHAR(64) NOT NULL DEFAULT '' COMMENT '能力点',
    `model`        VARCHAR(64) NOT NULL DEFAULT '' COMMENT '模型名称',
    `account_type` VARCHAR(16) NOT NULL DEFAULT '' COMMENT '账户类型',
    `account_code` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '账户编码',
    `ak_code`      VARCHAR(64)          DEFAULT NULL COMMENT 'ak编码',
    `amount`       DECIMAL(20, 4) NOT NULL DEFAULT 0.0000,
    `ctime`        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_account_type_code` (`account_type`, `account_code`),
    KEY `idx_ak_code_pt` (`ak_code`, `pt`),
    KEY `idx_ak_code_endpoint_model_pt` (`ak_code`, `endpoint`, `model`, `pt`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='每小时账单';

ALTER TABLE channel ADD COLUMN billing_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用计费(0:不计费,1:计费)';

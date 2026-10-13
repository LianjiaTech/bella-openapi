-- 为 apikey 表新增管理人字段
-- manager_code: 管理人编码（对应 user 的 userId 或 sourceId，与 owner_code 规则一致）
-- manager_name: 管理人姓名（冗余字段，用于展示）
ALTER TABLE apikey
    ADD COLUMN manager_code VARCHAR(64) DEFAULT '' NOT NULL COMMENT '管理人编码' AFTER owner_name,
    ADD COLUMN manager_name VARCHAR(16) DEFAULT '' NOT NULL COMMENT '管理人姓名' AFTER manager_code;

-- 为现有 person 类型的 apikey 初始化管理人为自身 owner
UPDATE apikey SET manager_code = owner_code, manager_name = owner_name WHERE owner_type = 'person';

-- 新增索引，支持按管理人查询
CREATE INDEX `idx_manager_code` ON apikey (`manager_code`);

SET NAMES utf8mb4;
CREATE TABLE `space`
(
    `id`                bigint unsigned                                               NOT NULL AUTO_INCREMENT COMMENT '主键',
    `space_code`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL DEFAULT '' COMMENT '空间编码',
    `space_name`        varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '空间名称',
    `space_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '空间描述',
    `status`            tinyint                                                       NOT NULL DEFAULT '0' COMMENT '删除状态(0未删除，-1已删除)',
    `ctime`             datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `mtime`             datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后一次更新时间',
    `owner_uid`         varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL DEFAULT '' COMMENT '空间拥有人系统号',
    `cuid`              bigint                                                        NOT NULL DEFAULT '0' COMMENT '空间创建人系统号',
    `muid`              bigint                                                        NOT NULL DEFAULT '0' COMMENT '空间最后一次更新人系统号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_idx_space_code` (`space_code`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='空间表';


CREATE TABLE `space_role`
(
    `id`         bigint unsigned                                              NOT NULL AUTO_INCREMENT COMMENT '主键',
    `space_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '团队编码',
    `role_code`  varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '角色编码',
    `role_name`  varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '角色名称',
    `role_desc`  varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '角色描述',
    `role_type`  tinyint unsigned                                                      DEFAULT '1' COMMENT '角色类型(1系统内置，2自定义)',
    `status`     tinyint                                                      NOT NULL DEFAULT '0' COMMENT '删除状态(0未删除，-1已删除)',
    `ctime`      datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `mtime`      datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后一次更新时间',
    `cuid`       bigint                                                       NOT NULL DEFAULT '0' COMMENT '创建人系统号',
    `muid`       bigint                                                       NOT NULL DEFAULT '0' COMMENT '最后一次更新人系统号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_idx_space_code_role_code` (`space_code`, `role_code`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='空间角色表';


CREATE TABLE `space_member`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `space_code`  varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '空间编码',
    `role_code`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '角色编码',
    `member_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '成员姓名',
    `member_uid`  varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '成员系统号',
    `status`      tinyint         NOT NULL DEFAULT '0' COMMENT '删除状态(0未删除，-1已删除)',
    `ctime`       datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `mtime`       datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后一次修改时间',
    `cuid`        bigint          NOT NULL DEFAULT '0' COMMENT '创建人系统号',
    `muid`        bigint          NOT NULL DEFAULT '0' COMMENT '最后一次更新人系统号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_member_uid` (`member_uid`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='空间成员信息表';

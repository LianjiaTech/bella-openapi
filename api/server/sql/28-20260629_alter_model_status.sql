alter table model
    add column deleted tinyint default 0 not null comment '是否删除(0:未删除,1:已删除)' after status;

ALTER TABLE `model`
  ADD COLUMN `openness_type` tinyint(4) NOT NULL DEFAULT 0
  COMMENT '开放程度(0:未知/1:闭源/2:开源)'
  AFTER `visibility`;

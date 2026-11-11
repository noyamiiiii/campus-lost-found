-- V2：认领补充对外编号、申请留言与联系方式
ALTER TABLE `claim`
  ADD COLUMN `public_id` VARCHAR(32) DEFAULT NULL COMMENT '对外编号 CL-YYYYMMDD-XXXX' AFTER `id`,
  ADD COLUMN `message`   VARCHAR(500) DEFAULT NULL COMMENT '申请说明/处理留言' AFTER `status`,
  ADD COLUMN `contact`   VARCHAR(120) DEFAULT NULL COMMENT '申请人留下的联系方式（仅发布者可见）' AFTER `message`,
  ADD UNIQUE KEY `uk_claim_public` (`public_id`);
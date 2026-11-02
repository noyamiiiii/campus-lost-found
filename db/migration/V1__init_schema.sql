-- 校园失物招领平台 初始化建表脚本
-- 依据：《需求分析与项目设计》5.2 数据表清单、5.3 关键字典、5.4 索引与容量估算
-- 目标库：MySQL 8.4（utf8mb4 / InnoDB）
-- 说明：本脚本尚未在真实库执行过，首次执行后请把结果写入 docs/test/README.md

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------- 用户与认证

CREATE TABLE `sys_user` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `public_id`         VARCHAR(32)  NOT NULL COMMENT '对外展示 ID（ULID）',
  `student_no`        VARCHAR(32)  DEFAULT NULL COMMENT '学号/工号',
  `real_name`         VARCHAR(64)  DEFAULT NULL COMMENT '真实姓名，仅认证与管理员可见',
  `phone`             VARCHAR(20)  DEFAULT NULL,
  `password_hash`     VARCHAR(100) DEFAULT NULL COMMENT '仅管理员账号使用密码登录',
  `role`              VARCHAR(20)  NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/SERVICE_ADMIN/ADMIN',
  `dept`              VARCHAR(64)  DEFAULT NULL COMMENT '院系或部门',
  `status`            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/BANNED/DELETED',
  `phone_verified_at` DATETIME(3)  DEFAULT NULL,
  `last_login_at`     DATETIME(3)  DEFAULT NULL,
  `deleted_at`        DATETIME(3)  DEFAULT NULL,
  `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_public_id` (`public_id`),
  UNIQUE KEY `uk_user_student_no` (`student_no`),
  UNIQUE KEY `uk_user_phone` (`phone`),
  KEY `idx_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户主表';

CREATE TABLE `user_profile` (
  `user_id`        BIGINT UNSIGNED NOT NULL,
  `avatar_file_id` BIGINT UNSIGNED DEFAULT NULL,
  `wechat`         VARCHAR(64)  DEFAULT NULL,
  `dorm`           VARCHAR(64)  DEFAULT NULL,
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_profile_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户扩展资料';

CREATE TABLE `identity_verification` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`          BIGINT UNSIGNED NOT NULL,
  `evidence_file_id` BIGINT UNSIGNED DEFAULT NULL,
  `status`           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `reviewer_id`      BIGINT UNSIGNED DEFAULT NULL,
  `reject_reason`    VARCHAR(255) DEFAULT NULL,
  `submitted_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `reviewed_at`      DATETIME(3)  DEFAULT NULL,
  `delete_after`     DATETIME(3)  DEFAULT NULL COMMENT '证明材料到期删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_verify_user` (`user_id`, `status`),
  KEY `idx_verify_status` (`status`, `submitted_at`),
  CONSTRAINT `fk_verify_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='身份认证申请';

CREATE TABLE `refresh_token` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `token_hash` CHAR(64)     NOT NULL COMMENT 'SHA-256 十六进制',
  `expires_at` DATETIME(3)  NOT NULL,
  `revoked_at` DATETIME(3)  DEFAULT NULL,
  `device`     VARCHAR(128) DEFAULT NULL,
  `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_hash` (`token_hash`),
  KEY `idx_refresh_user` (`user_id`, `expires_at`),
  CONSTRAINT `fk_refresh_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='刷新令牌（只存哈希）';

CREATE TABLE `consent_record` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT UNSIGNED NOT NULL,
  `policy_version` VARCHAR(20)  NOT NULL,
  `consented_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `ip`             VARCHAR(45)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consent` (`user_id`, `policy_version`),
  CONSTRAINT `fk_consent_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='隐私政策同意记录';

-- ---------------------------------------------------------------- 基础数据

CREATE TABLE `category` (
  `id`        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`      VARCHAR(32)  NOT NULL,
  `slug`      VARCHAR(32)  NOT NULL,
  `parent_id` BIGINT UNSIGNED DEFAULT NULL,
  `icon`      VARCHAR(64)  DEFAULT NULL,
  `sort`      INT          NOT NULL DEFAULT 0,
  `enabled`   TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_slug` (`slug`),
  KEY `idx_category_parent` (`parent_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物品分类';

CREATE TABLE `campus_location` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`             VARCHAR(64)  NOT NULL,
  `alias`            VARCHAR(128) DEFAULT NULL,
  `point`            POINT SRID 4326 NOT NULL COMMENT 'WGS84 经纬度',
  `building`         VARCHAR(64)  DEFAULT NULL,
  `type`             VARCHAR(20)  NOT NULL DEFAULT 'OTHER' COMMENT 'TEACHING/DORM/CANTEEN/SPORTS/OTHER',
  `is_service_point` TINYINT(1)   NOT NULL DEFAULT 0,
  `enabled`          TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_location_service` (`is_service_point`, `enabled`),
  SPATIAL KEY `sp_location_point` (`point`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='校园地点与失物招领点';

CREATE TABLE `file_object` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `owner_type`  VARCHAR(20)  NOT NULL COMMENT 'POST/USER/VERIFY/CLAIM',
  `owner_id`    BIGINT UNSIGNED DEFAULT NULL,
  `purpose`     VARCHAR(32)  NOT NULL COMMENT 'AVATAR/ITEM_IMAGE/EVIDENCE/CHAT',
  `storage_key` VARCHAR(255) NOT NULL,
  `thumb_key`   VARCHAR(255) DEFAULT NULL,
  `mime`        VARCHAR(100) NOT NULL,
  `size`        BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `sha256`      CHAR(64)     DEFAULT NULL,
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ORPHAN/DELETED',
  `expires_at`  DATETIME(3)  DEFAULT NULL,
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_storage_key` (`storage_key`),
  KEY `idx_file_owner` (`owner_type`, `owner_id`),
  KEY `idx_file_orphan` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一文件元数据';

CREATE TABLE `sys_config` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `cfg_key`    VARCHAR(64)  NOT NULL,
  `cfg_value`  VARCHAR(512) NOT NULL,
  `value_type` VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT 'STRING/INT/BOOL/JSON',
  `remark`     VARCHAR(255) DEFAULT NULL,
  `updated_by` BIGINT UNSIGNED DEFAULT NULL,
  `updated_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`cfg_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置';

-- ---------------------------------------------------------------- 帖子与图片

CREATE TABLE `item_post` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `public_id`      VARCHAR(32)  NOT NULL COMMENT '对外编号，如 LF-2026-1031-01',
  `user_id`        BIGINT UNSIGNED NOT NULL,
  `type`           VARCHAR(10)  NOT NULL COMMENT 'LOST 寻物 / FOUND 招领',
  `title`          VARCHAR(100) NOT NULL,
  `description`    TEXT         NOT NULL,
  `category_id`    BIGINT UNSIGNED NOT NULL,
  `color`          VARCHAR(16)  DEFAULT NULL,
  `color_tags`     JSON         DEFAULT NULL,
  `brand`          VARCHAR(64)  DEFAULT NULL,
  `location_id`    BIGINT UNSIGNED DEFAULT NULL,
  `lat`            DECIMAL(10,7) DEFAULT NULL,
  `lng`            DECIMAL(10,7) DEFAULT NULL,
  `detail`         VARCHAR(200) DEFAULT NULL COMMENT '具体位置补充说明',
  `event_time`     DATETIME(3)  NOT NULL COMMENT '丢失/拾取发生时间',
  `status`         VARCHAR(24)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/CLAIMING/CLAIMED/HANDOVER_FAILED/RETURNED/EXPIRED/CLOSED',
  `audit_status`   VARCHAR(24)  NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT 'NOT_SUBMITTED/PENDING/APPROVED/REJECTED/BLOCKED',
  `visibility`     VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/MASKED/PRIVATE',
  `contact_mode`   VARCHAR(20)  NOT NULL DEFAULT 'IN_APP_ONLY' COMMENT 'PUBLIC/AFTER_CLAIM/IN_APP_ONLY',
  `view_count`     INT UNSIGNED NOT NULL DEFAULT 0,
  `match_count`    INT UNSIGNED NOT NULL DEFAULT 0,
  `extend_count`   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '续期次数，最多 1 次',
  `expire_at`      DATETIME(3)  DEFAULT NULL,
  `published_at`   DATETIME(3)  DEFAULT NULL,
  `closed_at`      DATETIME(3)  DEFAULT NULL,
  `returned_at`    DATETIME(3)  DEFAULT NULL,
  `version`        INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `deleted_at`     DATETIME(3)  DEFAULT NULL,
  `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_public_id` (`public_id`),
  KEY `idx_post_feed` (`audit_status`, `status`, `type`, `created_at`, `id`),
  KEY `idx_post_expire` (`status`, `expire_at`),
  KEY `idx_post_location_time` (`location_id`, `event_time`),
  KEY `idx_post_user` (`user_id`, `created_at`),
  KEY `idx_post_category` (`category_id`, `status`, `created_at`),
  FULLTEXT KEY `ft_post_text` (`title`, `description`) WITH PARSER ngram,
  CONSTRAINT `fk_post_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_post_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`),
  CONSTRAINT `fk_post_location` FOREIGN KEY (`location_id`) REFERENCES `campus_location` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='失物/招领帖子';

CREATE TABLE `item_image` (
  `id`                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `post_id`                BIGINT UNSIGNED NOT NULL,
  `file_id`                BIGINT UNSIGNED NOT NULL,
  `width`                  INT UNSIGNED DEFAULT NULL,
  `height`                 INT UNSIGNED DEFAULT NULL,
  `is_primary`             TINYINT(1)   NOT NULL DEFAULT 0,
  `ai_labels`              JSON         DEFAULT NULL,
  `sensitive_regions`      JSON         DEFAULT NULL COMMENT '敏感区域坐标，打码用',
  `ocr_text`               TEXT         DEFAULT NULL COMMENT 'P1 启用',
  `embedding`              LONGBLOB     DEFAULT NULL,
  `embedding_model`        VARCHAR(64)  DEFAULT NULL,
  `embedding_version`      VARCHAR(32)  DEFAULT NULL,
  `embedding_created_at`   DATETIME(3)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_image_post` (`post_id`, `is_primary`),
  KEY `idx_image_embedding` (`embedding_model`, `embedding_version`),
  CONSTRAINT `fk_image_post` FOREIGN KEY (`post_id`) REFERENCES `item_post` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_image_file` FOREIGN KEY (`file_id`) REFERENCES `file_object` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子图片与向量';

CREATE TABLE `post_status_history` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `post_id`     BIGINT UNSIGNED NOT NULL,
  `from_status` VARCHAR(24)  DEFAULT NULL,
  `to_status`   VARCHAR(24)  NOT NULL,
  `actor_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT 'NULL 表示系统触发',
  `reason`      VARCHAR(255) DEFAULT NULL,
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_post_history` (`post_id`, `created_at`),
  CONSTRAINT `fk_post_history_post` FOREIGN KEY (`post_id`) REFERENCES `item_post` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子状态流转历史';

-- ---------------------------------------------------------------- 匹配

CREATE TABLE `match_record` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `source_post_id`    BIGINT UNSIGNED NOT NULL,
  `target_post_id`    BIGINT UNSIGNED NOT NULL,
  `total_score`       DECIMAL(5,4) NOT NULL DEFAULT 0,
  `img_score`         DECIMAL(5,4) DEFAULT NULL,
  `text_score`        DECIMAL(5,4) DEFAULT NULL,
  `loc_score`         DECIMAL(5,4) DEFAULT NULL,
  `time_score`        DECIMAL(5,4) DEFAULT NULL,
  `algorithm_version` VARCHAR(32)  NOT NULL,
  `status`            VARCHAR(16)  NOT NULL DEFAULT 'NEW' COMMENT 'NEW/VIEWED/ACCEPTED/REJECTED/EXPIRED',
  `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_pair` (`source_post_id`, `target_post_id`),
  KEY `idx_match_status` (`status`, `created_at`),
  KEY `idx_match_target` (`target_post_id`, `status`),
  CONSTRAINT `fk_match_source` FOREIGN KEY (`source_post_id`) REFERENCES `item_post` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_match_target` FOREIGN KEY (`target_post_id`) REFERENCES `item_post` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配记录';

CREATE TABLE `match_task` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `post_id`       BIGINT UNSIGNED NOT NULL,
  `task_type`     VARCHAR(20)  NOT NULL DEFAULT 'EMBED_AND_MATCH' COMMENT 'EMBED/MATCH/RECOGNIZE',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
  `retry_count`   TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `next_retry_at` DATETIME(3)  DEFAULT NULL,
  `last_error`    VARCHAR(500) DEFAULT NULL,
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_task_poll` (`status`, `next_retry_at`),
  KEY `idx_task_post` (`post_id`, `task_type`),
  CONSTRAINT `fk_task_post` FOREIGN KEY (`post_id`) REFERENCES `item_post` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异步任务 outbox';

-- ---------------------------------------------------------------- 认领与核销

CREATE TABLE `claim` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `post_id`            BIGINT UNSIGNED NOT NULL,
  `applicant_id`       BIGINT UNSIGNED NOT NULL,
  `status`             VARCHAR(24)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/REJECTED/HANDOVER_FAILED/COMPLETED/CANCELED/EXPIRED',
  `active_flag`        TINYINT GENERATED ALWAYS AS (CASE WHEN `status` IN ('PENDING','CONFIRMED') THEN 1 ELSE NULL END) STORED,
  `evidence_file_id`   BIGINT UNSIGNED DEFAULT NULL,
  `verify_code_hash`   CHAR(64)     DEFAULT NULL COMMENT '一次性核销码哈希',
  `verify_expires_at`  DATETIME(3)  DEFAULT NULL,
  `verify_attempts`    TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `version`            INT UNSIGNED NOT NULL DEFAULT 0,
  `confirmed_at`       DATETIME(3)  DEFAULT NULL,
  `completed_at`       DATETIME(3)  DEFAULT NULL,
  `canceled_at`        DATETIME(3)  DEFAULT NULL,
  `created_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_claim_active` (`post_id`, `active_flag`),
  KEY `idx_claim_applicant` (`applicant_id`, `status`),
  KEY `idx_claim_status` (`status`, `created_at`),
  CONSTRAINT `fk_claim_post` FOREIGN KEY (`post_id`) REFERENCES `item_post` (`id`),
  CONSTRAINT `fk_claim_user` FOREIGN KEY (`applicant_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='认领申请（同一帖子同时只能有一个进行中）';

CREATE TABLE `claim_question` (
  `id`       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `post_id`  BIGINT UNSIGNED NOT NULL,
  `question` VARCHAR(200) NOT NULL,
  `sort`     TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `required` TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_question_post` (`post_id`, `sort`),
  CONSTRAINT `fk_question_post` FOREIGN KEY (`post_id`) REFERENCES `item_post` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招领特征问题（1-3 个）';

CREATE TABLE `claim_answer` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `claim_id`            BIGINT UNSIGNED NOT NULL,
  `question_id`         BIGINT UNSIGNED NOT NULL,
  `answer_ciphertext`   VARBINARY(512) NOT NULL COMMENT 'AES-GCM 密文，禁止明文存储',
  `created_at`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_answer` (`claim_id`, `question_id`),
  CONSTRAINT `fk_answer_claim` FOREIGN KEY (`claim_id`) REFERENCES `claim` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_answer_question` FOREIGN KEY (`question_id`) REFERENCES `claim_question` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='认领答案（加密存储）';

CREATE TABLE `claim_status_history` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `claim_id`    BIGINT UNSIGNED NOT NULL,
  `from_status` VARCHAR(24)  DEFAULT NULL,
  `to_status`   VARCHAR(24)  NOT NULL,
  `actor_id`    BIGINT UNSIGNED DEFAULT NULL,
  `reason`      VARCHAR(255) DEFAULT NULL,
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_claim_history` (`claim_id`, `created_at`),
  CONSTRAINT `fk_claim_history_claim` FOREIGN KEY (`claim_id`) REFERENCES `claim` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='认领状态流转历史';

CREATE TABLE `custody_record` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `post_id`          BIGINT UNSIGNED NOT NULL,
  `service_point_id` BIGINT UNSIGNED NOT NULL,
  `direction`        VARCHAR(8)   NOT NULL COMMENT 'IN 入库 / OUT 出库',
  `keeper_id`        BIGINT UNSIGNED NOT NULL,
  `handled_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `note`             VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_custody_post` (`post_id`, `handled_at`),
  KEY `idx_custody_point` (`service_point_id`, `direction`),
  CONSTRAINT `fk_custody_post` FOREIGN KEY (`post_id`) REFERENCES `item_post` (`id`),
  CONSTRAINT `fk_custody_point` FOREIGN KEY (`service_point_id`) REFERENCES `campus_location` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务点交接记录';

-- ---------------------------------------------------------------- 沟通与通知

CREATE TABLE `conversation` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `post_id`     BIGINT UNSIGNED NOT NULL,
  `user_a`      BIGINT UNSIGNED NOT NULL COMMENT '约定 user_a < user_b',
  `user_b`      BIGINT UNSIGNED NOT NULL,
  `last_msg_id` BIGINT UNSIGNED DEFAULT NULL,
  `last_msg_at` DATETIME(3)  DEFAULT NULL,
  `unread_a`    INT UNSIGNED NOT NULL DEFAULT 0,
  `unread_b`    INT UNSIGNED NOT NULL DEFAULT 0,
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation` (`post_id`, `user_a`, `user_b`),
  KEY `idx_conv_user_a` (`user_a`, `last_msg_at`),
  KEY `idx_conv_user_b` (`user_b`, `last_msg_at`),
  CONSTRAINT `fk_conv_post` FOREIGN KEY (`post_id`) REFERENCES `item_post` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_conv_user_a` FOREIGN KEY (`user_a`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_conv_user_b` FOREIGN KEY (`user_b`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `ck_conv_order` CHECK (`user_a` < `user_b`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内会话';

CREATE TABLE `message` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conv_id`           BIGINT UNSIGNED NOT NULL,
  `from_id`           BIGINT UNSIGNED NOT NULL,
  `client_msg_id`     VARCHAR(64)  NOT NULL COMMENT '客户端生成，防重复发送',
  `content`           TEXT         NOT NULL,
  `type`              VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE/SYSTEM',
  `file_id`           BIGINT UNSIGNED DEFAULT NULL,
  `moderation_status` VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PASS/BLOCK',
  `read_at`           DATETIME(3)  DEFAULT NULL,
  `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_client` (`conv_id`, `from_id`, `client_msg_id`),
  KEY `idx_message_page` (`conv_id`, `created_at`, `id`),
  CONSTRAINT `fk_message_conv` FOREIGN KEY (`conv_id`) REFERENCES `conversation` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_message_from` FOREIGN KEY (`from_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内消息';

CREATE TABLE `notification` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED NOT NULL,
  `category`    VARCHAR(16)  NOT NULL COMMENT 'MATCH/CLAIM/AUDIT/SYSTEM',
  `title`       VARCHAR(100) NOT NULL,
  `content`     VARCHAR(500) NOT NULL,
  `ref_type`    VARCHAR(20)  DEFAULT NULL COMMENT 'POST/CLAIM/MESSAGE',
  `ref_id`      BIGINT UNSIGNED DEFAULT NULL,
  `dedupe_key`  VARCHAR(100) DEFAULT NULL COMMENT '同一事件只推一次',
  `read_at`     DATETIME(3)  DEFAULT NULL,
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_dedupe` (`user_id`, `dedupe_key`),
  KEY `idx_notification_list` (`user_id`, `read_at`, `created_at`),
  CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知';

-- ---------------------------------------------------------------- 治理与运营

CREATE TABLE `report` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `target_type` VARCHAR(20)  NOT NULL COMMENT 'POST/USER/MESSAGE',
  `target_id`   BIGINT UNSIGNED NOT NULL,
  `reporter_id` BIGINT UNSIGNED NOT NULL,
  `reason`      VARCHAR(32)  NOT NULL,
  `detail`      VARCHAR(500) DEFAULT NULL,
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/HANDLING/RESOLVED/REJECTED',
  `handler_id`  BIGINT UNSIGNED DEFAULT NULL,
  `result`      VARCHAR(255) DEFAULT NULL,
  `handled_at`  DATETIME(3)  DEFAULT NULL,
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_report_status` (`status`, `created_at`),
  KEY `idx_report_target` (`target_type`, `target_id`),
  CONSTRAINT `fk_report_user` FOREIGN KEY (`reporter_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举报';

CREATE TABLE `appeal` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `report_id`  BIGINT UNSIGNED NOT NULL,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `reason`     VARCHAR(500) NOT NULL,
  `status`     VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/ACCEPTED/REJECTED',
  `handler_id` BIGINT UNSIGNED DEFAULT NULL,
  `result`     VARCHAR(255) DEFAULT NULL,
  `handled_at` DATETIME(3)  DEFAULT NULL,
  `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_appeal_status` (`status`, `created_at`),
  CONSTRAINT `fk_appeal_report` FOREIGN KEY (`report_id`) REFERENCES `report` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_appeal_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='申诉';

CREATE TABLE `admin_audit_log` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id`     BIGINT UNSIGNED NOT NULL,
  `action`       VARCHAR(64)  NOT NULL,
  `target_type`  VARCHAR(32)  NOT NULL,
  `target_id`    BIGINT UNSIGNED DEFAULT NULL,
  `before_json`  JSON         DEFAULT NULL,
  `after_json`   JSON         DEFAULT NULL,
  `request_id`   VARCHAR(64)  DEFAULT NULL,
  `ip`           VARCHAR(45)  DEFAULT NULL,
  `user_agent`   VARCHAR(255) DEFAULT NULL,
  `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_admin` (`admin_id`, `created_at`),
  KEY `idx_audit_target` (`target_type`, `target_id`),
  CONSTRAINT `fk_audit_admin` FOREIGN KEY (`admin_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理端审计日志';

CREATE TABLE `review` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `claim_id`   BIGINT UNSIGNED NOT NULL,
  `from_id`    BIGINT UNSIGNED NOT NULL,
  `to_id`      BIGINT UNSIGNED NOT NULL,
  `rating`     TINYINT UNSIGNED NOT NULL COMMENT '1-5',
  `content`    VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_once` (`claim_id`, `from_id`),
  KEY `idx_review_to` (`to_id`, `created_at`),
  CONSTRAINT `fk_review_claim` FOREIGN KEY (`claim_id`) REFERENCES `claim` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_review_from` FOREIGN KEY (`from_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_review_to` FOREIGN KEY (`to_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `ck_review_rating` CHECK (`rating` BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价（P2）';

CREATE TABLE `credit_log` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `rule_id`       VARCHAR(32)  NOT NULL,
  `delta`         INT          NOT NULL,
  `balance_after` INT          NOT NULL,
  `reason`        VARCHAR(255) DEFAULT NULL,
  `ref_id`        BIGINT UNSIGNED DEFAULT NULL,
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_credit_user` (`user_id`, `created_at`),
  CONSTRAINT `fk_credit_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='信用分变更（P2）';

CREATE TABLE `favorite` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `post_id`    BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite` (`user_id`, `post_id`),
  KEY `idx_favorite_post` (`post_id`),
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_favorite_post` FOREIGN KEY (`post_id`) REFERENCES `item_post` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏（P2）';

-- ---------------------------------------------------------------- 基础设施

CREATE TABLE `idempotency_key` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `endpoint`      VARCHAR(128) NOT NULL,
  `idem_key`      VARCHAR(64)  NOT NULL,
  `request_hash`  CHAR(64)     NOT NULL,
  `response_json` JSON         DEFAULT NULL,
  `expires_at`    DATETIME(3)  NOT NULL,
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idem` (`user_id`, `endpoint`, `idem_key`),
  KEY `idx_idem_expire` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口幂等键';

SET FOREIGN_KEY_CHECKS = 1;

-- 图片理解结果落到 file_object：上传时算一次，发帖时再复制到 item_image。
ALTER TABLE `file_object`
    ADD COLUMN `ai_status`           VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/DONE/FAILED/SKIPPED',
    ADD COLUMN `ai_category_id`      INT UNSIGNED  DEFAULT NULL COMMENT '识别出的分类 id，对应 category 表',
    ADD COLUMN `ai_confidence`       DECIMAL(6, 4) DEFAULT NULL COMMENT '分类置信度 0~1',
    ADD COLUMN `ai_labels`           JSON          DEFAULT NULL COMMENT '模型前 5 个标签',
    ADD COLUMN `ai_colors`           JSON          DEFAULT NULL COMMENT '主色数组 [{hex,name,ratio}]',
    ADD COLUMN `ai_sensitive`        JSON          DEFAULT NULL COMMENT '敏感区域 {hasFace,hasIdCard,boxes}',
    ADD COLUMN `ai_description`      VARCHAR(1000) DEFAULT NULL COMMENT '拼装出的图文描述',
    ADD COLUMN `ai_title`            VARCHAR(120)  DEFAULT NULL COMMENT '建议标题',
    ADD COLUMN `ai_model`            VARCHAR(64)   DEFAULT NULL COMMENT '模型标识，例如 mobilenetv2-7',
    ADD COLUMN `ai_analyzed_at`      DATETIME(3)   DEFAULT NULL,
    ADD COLUMN `embedding`           LONGBLOB      DEFAULT NULL COMMENT '图像向量 float32，L2 归一化',
    ADD COLUMN `embedding_model`     VARCHAR(64)   DEFAULT NULL,
    ADD COLUMN `embedding_version`   VARCHAR(32)   DEFAULT NULL,
    ADD COLUMN `embedding_created_at` DATETIME(3)  DEFAULT NULL;
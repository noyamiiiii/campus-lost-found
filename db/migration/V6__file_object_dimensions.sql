-- 图片尺寸：上传时解析一次并落库，列表/详情直接返回，前端可预留占位避免布局跳动。
ALTER TABLE file_object
    ADD COLUMN width INT NULL COMMENT '像素宽，非图片或解析失败为 NULL' AFTER sha256,
    ADD COLUMN height INT NULL COMMENT '像素高，非图片或解析失败为 NULL' AFTER width;
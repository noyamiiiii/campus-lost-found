package edu.campus.lostfound.post;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("item_image")
public class ItemImageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long fileId;
    private Integer width;
    private Integer height;
    private Boolean isPrimary;
    private String aiLabels;
    private String sensitiveRegions;
    private String ocrText;
    private byte[] embedding;
    private String embeddingModel;
    private String embeddingVersion;
    private LocalDateTime embeddingCreatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public String getAiLabels() {
        return aiLabels;
    }

    public void setAiLabels(String aiLabels) {
        this.aiLabels = aiLabels;
    }

    public String getSensitiveRegions() {
        return sensitiveRegions;
    }

    public void setSensitiveRegions(String sensitiveRegions) {
        this.sensitiveRegions = sensitiveRegions;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getEmbeddingVersion() {
        return embeddingVersion;
    }

    public void setEmbeddingVersion(String embeddingVersion) {
        this.embeddingVersion = embeddingVersion;
    }

    public LocalDateTime getEmbeddingCreatedAt() {
        return embeddingCreatedAt;
    }

    public void setEmbeddingCreatedAt(LocalDateTime embeddingCreatedAt) {
        this.embeddingCreatedAt = embeddingCreatedAt;
    }
}
package edu.campus.lostfound.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("file_object")
public class FileObjectEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ownerType;
    private Long ownerId;
    private String purpose;
    private String storageKey;
    private String thumbKey;
    private String mime;
    private Long size;
    private String sha256;
    private Integer width;
    private Integer height;
    private String status;
    private LocalDateTime expiresAt;

    private String aiStatus;
    private Integer aiCategoryId;
    private BigDecimal aiConfidence;
    private String aiLabels;
    private String aiColors;
    private String aiSensitive;
    private String aiDescription;
    private String aiTitle;
    private String aiModel;
    private LocalDateTime aiAnalyzedAt;
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

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getThumbKey() {
        return thumbKey;
    }

    public void setThumbKey(String thumbKey) {
        this.thumbKey = thumbKey;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getSha256() {
        return sha256;
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

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getAiStatus() {
        return aiStatus;
    }

    public void setAiStatus(String aiStatus) {
        this.aiStatus = aiStatus;
    }

    public Integer getAiCategoryId() {
        return aiCategoryId;
    }

    public void setAiCategoryId(Integer aiCategoryId) {
        this.aiCategoryId = aiCategoryId;
    }

    public BigDecimal getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(BigDecimal aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public String getAiLabels() {
        return aiLabels;
    }

    public void setAiLabels(String aiLabels) {
        this.aiLabels = aiLabels;
    }

    public String getAiColors() {
        return aiColors;
    }

    public void setAiColors(String aiColors) {
        this.aiColors = aiColors;
    }

    public String getAiSensitive() {
        return aiSensitive;
    }

    public void setAiSensitive(String aiSensitive) {
        this.aiSensitive = aiSensitive;
    }

    public String getAiDescription() {
        return aiDescription;
    }

    public void setAiDescription(String aiDescription) {
        this.aiDescription = aiDescription;
    }

    public String getAiTitle() {
        return aiTitle;
    }

    public void setAiTitle(String aiTitle) {
        this.aiTitle = aiTitle;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public LocalDateTime getAiAnalyzedAt() {
        return aiAnalyzedAt;
    }

    public void setAiAnalyzedAt(LocalDateTime aiAnalyzedAt) {
        this.aiAnalyzedAt = aiAnalyzedAt;
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
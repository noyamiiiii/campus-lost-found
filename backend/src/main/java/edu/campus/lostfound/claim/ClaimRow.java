package edu.campus.lostfound.claim;

import java.time.LocalDateTime;

/**
 * 认领列表 / 详情的查询结果行（含帖子与双方申请人信息）。
 */
public class ClaimRow {

    private Long id;
    private String publicId;
    private Long postId;
    private String postPublicId;
    private String postTitle;
    private String postStatus;
    private Long applicantId;
    private String applicantPublicId;
    private String applicantName;
    private Long ownerId;
    private String ownerPublicId;
    private String ownerName;
    private String status;
    private String message;
    private String contact;
    private Long evidenceFileId;
    private LocalDateTime confirmedAt;
    private LocalDateTime completedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getPostPublicId() {
        return postPublicId;
    }

    public void setPostPublicId(String postPublicId) {
        this.postPublicId = postPublicId;
    }

    public String getPostTitle() {
        return postTitle;
    }

    public void setPostTitle(String postTitle) {
        this.postTitle = postTitle;
    }

    public String getPostStatus() {
        return postStatus;
    }

    public void setPostStatus(String postStatus) {
        this.postStatus = postStatus;
    }

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public String getApplicantPublicId() {
        return applicantPublicId;
    }

    public void setApplicantPublicId(String applicantPublicId) {
        this.applicantPublicId = applicantPublicId;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerPublicId() {
        return ownerPublicId;
    }

    public void setOwnerPublicId(String ownerPublicId) {
        this.ownerPublicId = ownerPublicId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public Long getEvidenceFileId() {
        return evidenceFileId;
    }

    public void setEvidenceFileId(Long evidenceFileId) {
        this.evidenceFileId = evidenceFileId;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }

    public void setCanceledAt(LocalDateTime canceledAt) {
        this.canceledAt = canceledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
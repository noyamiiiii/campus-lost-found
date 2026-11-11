package edu.campus.lostfound.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "请补充一点线索说明")
            @Size(min = 5, max = 500, message = "归属说明请写 5 到 500 字")
            String message,
            @Size(max = 120, message = "联系方式最多 120 字")
            String contact,
            Long evidenceFileId) {
    }

    public record MessageRequest(
            @NotBlank(message = "请填写处理说明")
            @Size(max = 500, message = "说明最多 500 字")
            String message) {
    }

    public record ClaimView(
            String publicId,
            String postPublicId,
            String postTitle,
            String postStatus,
            String applicantPublicId,
            String applicantName,
            String ownerPublicId,
            String ownerName,
            String status,
            String message,
            String contact,
            Long evidenceFileId,
            OffsetDateTime confirmedAt,
            OffsetDateTime completedAt,
            OffsetDateTime canceledAt,
            OffsetDateTime createdAt) {
    }
}
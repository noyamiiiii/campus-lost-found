package edu.campus.lostfound.post;

import java.time.OffsetDateTime;
import java.util.List;

public final class PostDtos {

    private PostDtos() {
    }

    public record PostSummary(
            String publicId,
            String type,
            String title,
            String description,
            Long categoryId,
            String categoryName,
            String locationName,
            OffsetDateTime eventTime,
            String status,
            String auditStatus,
            Integer matchCount,
            String publisherName
    ) {
    }

    public record PostDetail(
            String publicId,
            String type,
            String title,
            String description,
            Long categoryId,
            String categoryName,
            String locationName,
            String locationDetail,
            String brand,
            String color,
            OffsetDateTime eventTime,
            String status,
            String auditStatus,
            String contactMode,
            Integer matchCount,
            String publisherName,
            OffsetDateTime expireAt
    ) {
    }

    public record PostPage(List<PostSummary> list, String nextCursor, boolean hasMore) {
    }
}

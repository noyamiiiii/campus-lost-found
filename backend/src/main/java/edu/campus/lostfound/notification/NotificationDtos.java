package edu.campus.lostfound.notification;

import java.time.OffsetDateTime;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationView(
            Long id,
            String category,
            String title,
            String content,
            String refType,
            Long refId,
            boolean read,
            OffsetDateTime createdAt
    ) {
    }

    public record UnreadCountData(int count) {
    }
}
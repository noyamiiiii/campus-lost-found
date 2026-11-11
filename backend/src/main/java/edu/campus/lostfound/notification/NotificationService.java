package edu.campus.lostfound.notification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.campus.lostfound.common.ApiException;
import edu.campus.lostfound.notification.NotificationDtos.NotificationView;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站内通知：业务事件（认领发起/同意/拒绝/交接完成）落库后由这里统一写入，
 * 前端轮询未读数，点开即标记已读。
 */
@Service
@Profile("local")
public class NotificationService {

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> CATEGORIES = Set.of("MATCH", "CLAIM", "AUDIT", "SYSTEM");
    private static final int LIST_LIMIT = 50;

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 写入一条通知。dedupeKey 相同的事件只落一条（可重复调用）。
     */
    @Transactional
    public void notify(Long userId, String category, String title, String content,
                       String refType, Long refId, String dedupeKey) {
        if (userId == null) {
            return;
        }
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(userId);
        entity.setCategory(CATEGORIES.contains(category) ? category : "SYSTEM");
        entity.setTitle(truncate(title, 100));
        entity.setContent(truncate(content, 500));
        entity.setRefType(refType);
        entity.setRefId(refId);
        entity.setDedupeKey(dedupeKey);
        try {
            notificationMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            // 同一事件重复触发时忽略
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationView> list(Long userId) {
        return notificationMapper.selectList(Wrappers.<NotificationEntity>lambdaQuery()
                        .eq(NotificationEntity::getUserId, userId)
                        .orderByDesc(NotificationEntity::getCreatedAt)
                        .orderByDesc(NotificationEntity::getId)
                        .last("LIMIT " + LIST_LIMIT))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public int unreadCount(Long userId) {
        Long count = notificationMapper.selectCount(Wrappers.<NotificationEntity>lambdaQuery()
                .eq(NotificationEntity::getUserId, userId)
                .isNull(NotificationEntity::getReadAt));
        return count == null ? 0 : count.intValue();
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        NotificationEntity entity = notificationMapper.selectById(notificationId);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw ApiException.notFound("通知不存在");
        }
        if (entity.getReadAt() != null) {
            return;
        }
        entity.setReadAt(LocalDateTime.now(CAMPUS_ZONE));
        notificationMapper.updateById(entity);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationMapper.update(null, Wrappers.<NotificationEntity>lambdaUpdate()
                .eq(NotificationEntity::getUserId, userId)
                .isNull(NotificationEntity::getReadAt)
                .set(NotificationEntity::getReadAt, LocalDateTime.now(CAMPUS_ZONE)));
    }

    private NotificationView toView(NotificationEntity entity) {
        return new NotificationView(
                entity.getId(),
                entity.getCategory(),
                entity.getTitle(),
                entity.getContent(),
                entity.getRefType(),
                entity.getRefId(),
                entity.getReadAt() != null,
                toOffsetDateTime(entity.getCreatedAt()));
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(CAMPUS_ZONE).toOffsetDateTime();
    }

    private String truncate(String value, int max) {
        String normalized = value == null || value.isBlank() ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
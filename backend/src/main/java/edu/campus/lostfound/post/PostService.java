package edu.campus.lostfound.post;

import edu.campus.lostfound.common.ApiException;
import edu.campus.lostfound.post.PostCursorCodec.Cursor;
import edu.campus.lostfound.post.PostDtos.PostDetail;
import edu.campus.lostfound.post.PostDtos.PostPage;
import edu.campus.lostfound.post.PostDtos.PostSummary;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local")
public class PostService {

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> POST_TYPES = Set.of("LOST", "FOUND");

    private final PostQueryMapper postQueryMapper;
    private final PostCursorCodec cursorCodec;

    public PostService(PostQueryMapper postQueryMapper, PostCursorCodec cursorCodec) {
        this.postQueryMapper = postQueryMapper;
        this.cursorCodec = cursorCodec;
    }

    @Transactional(readOnly = true)
    public PostPage list(String type, Long categoryId, String keyword, String encodedCursor, int size) {
        String normalizedType = normalizeType(type);
        String normalizedKeyword = normalizeKeyword(keyword);
        if (categoryId != null && categoryId < 1) {
            throw ApiException.badRequest("categoryId 必须为正整数");
        }
        if (size < 1 || size > 50) {
            throw ApiException.badRequest("size 必须在 1 到 50 之间");
        }

        Cursor cursor = encodedCursor == null || encodedCursor.isBlank()
                ? null
                : cursorCodec.decode(encodedCursor.trim());
        LocalDateTime cursorTime = cursor == null ? null : cursor.createdAt();
        Long cursorId = cursor == null ? null : cursor.id();

        List<PostRow> rows = postQueryMapper.findPage(
                normalizedType,
                categoryId,
                normalizedKeyword,
                cursorTime,
                cursorId,
                size + 1);

        boolean hasMore = rows.size() > size;
        List<PostRow> pageRows = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore && !pageRows.isEmpty()
                ? cursorCodec.encode(pageRows.get(pageRows.size() - 1))
                : null;

        return new PostPage(pageRows.stream().map(this::toSummary).toList(), nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public PostDetail get(String publicId) {
        PostRow row = postQueryMapper.findByPublicId(publicId);
        if (row == null) {
            throw ApiException.notFound("帖子不存在或不可见");
        }
        return new PostDetail(
                row.getPublicId(),
                row.getType(),
                row.getTitle(),
                row.getDescription(),
                row.getCategoryId(),
                row.getCategoryName(),
                row.getLocationName(),
                row.getLocationDetail(),
                row.getBrand(),
                row.getColor(),
                toOffsetDateTime(row.getEventTime()),
                row.getStatus(),
                row.getAuditStatus(),
                row.getContactMode(),
                row.getMatchCount(),
                publisherName(row),
                toOffsetDateTime(row.getExpireAt()));
    }

    private PostSummary toSummary(PostRow row) {
        return new PostSummary(
                row.getPublicId(),
                row.getType(),
                row.getTitle(),
                row.getDescription(),
                row.getCategoryId(),
                row.getCategoryName(),
                row.getLocationName(),
                toOffsetDateTime(row.getEventTime()),
                row.getStatus(),
                row.getAuditStatus(),
                row.getMatchCount(),
                publisherName(row));
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!POST_TYPES.contains(normalized)) {
            throw ApiException.badRequest("type 只允许 LOST 或 FOUND");
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 64) {
            throw ApiException.badRequest("keyword 不能超过 64 个字符");
        }
        return normalized;
    }

    private String publisherName(PostRow row) {
        String publicId = row.getPublisherPublicId();
        String suffix = publicId != null && publicId.length() > 4
                ? publicId.substring(publicId.length() - 4)
                : "匿名";
        return "校园用户 " + suffix;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(CAMPUS_ZONE).toOffsetDateTime();
    }
}

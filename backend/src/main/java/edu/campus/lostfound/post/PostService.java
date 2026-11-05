package edu.campus.lostfound.post;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.campus.lostfound.catalog.CampusLocationEntity;
import edu.campus.lostfound.catalog.CampusLocationMapper;
import edu.campus.lostfound.catalog.CategoryEntity;
import edu.campus.lostfound.catalog.CategoryMapper;
import edu.campus.lostfound.common.ApiException;
import edu.campus.lostfound.post.PostCursorCodec.Cursor;
import edu.campus.lostfound.post.PostDtos.PostCreateRequest;
import edu.campus.lostfound.post.PostDtos.PostDetail;
import edu.campus.lostfound.post.PostDtos.PostPage;
import edu.campus.lostfound.post.PostDtos.PostSummary;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local")
public class PostService {

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> POST_TYPES = Set.of("LOST", "FOUND");
    private static final Set<String> CONTACT_MODES = Set.of("PUBLIC", "AFTER_CLAIM", "IN_APP_ONLY");
    private static final DateTimeFormatter PUBLIC_ID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PUBLIC_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int EXPIRE_DAYS = 30;
    private static final int MINE_LIMIT = 50;

    private final PostQueryMapper postQueryMapper;
    private final PostCursorCodec cursorCodec;
    private final ItemPostMapper itemPostMapper;
    private final CategoryMapper categoryMapper;
    private final CampusLocationMapper locationMapper;

    public PostService(
            PostQueryMapper postQueryMapper,
            PostCursorCodec cursorCodec,
            ItemPostMapper itemPostMapper,
            CategoryMapper categoryMapper,
            CampusLocationMapper locationMapper
    ) {
        this.postQueryMapper = postQueryMapper;
        this.cursorCodec = cursorCodec;
        this.itemPostMapper = itemPostMapper;
        this.categoryMapper = categoryMapper;
        this.locationMapper = locationMapper;
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

    @Transactional(readOnly = true)
    public List<PostSummary> listMine(Long userId) {
        return postQueryMapper.findMine(userId, MINE_LIMIT).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public PostDetail create(Long userId, PostCreateRequest request) {
        String type = normalizeType(request.type());
        if (type == null) {
            throw ApiException.badRequest("type 只允许 LOST 或 FOUND");
        }
        if (!categoryExists(request.categoryId())) {
            throw ApiException.badRequest("物品分类不存在或已停用");
        }
        Long locationId = normalizeLocation(request.locationId());
        String contactMode = normalizeContactMode(request.contactMode());

        LocalDateTime now = LocalDateTime.now(CAMPUS_ZONE);
        LocalDateTime eventTime = request.eventTime()
                .atZoneSameInstant(CAMPUS_ZONE)
                .toLocalDateTime();
        if (eventTime.isAfter(now.plusMinutes(10))) {
            throw ApiException.badRequest("发生时间不能晚于当前时间");
        }
        if (eventTime.isBefore(now.minusDays(365))) {
            throw ApiException.badRequest("发生时间不能早于一年前");
        }

        ItemPostEntity entity = new ItemPostEntity();
        entity.setUserId(userId);
        entity.setType(type);
        entity.setTitle(request.title().trim());
        entity.setDescription(request.description().trim());
        entity.setCategoryId(request.categoryId());
        entity.setLocationId(locationId);
        entity.setDetail(normalizeNullable(request.detail()));
        entity.setBrand(normalizeNullable(request.brand()));
        entity.setColor(normalizeNullable(request.color()));
        entity.setEventTime(eventTime);
        entity.setStatus("ACTIVE");
        entity.setAuditStatus("APPROVED");
        entity.setVisibility("PUBLIC");
        entity.setContactMode(contactMode);
        entity.setViewCount(0);
        entity.setMatchCount(0);
        entity.setExtendCount(0);
        entity.setExpireAt(now.plusDays(EXPIRE_DAYS));
        entity.setPublishedAt(now);
        entity.setVersion(0);

        String publicId = insertWithUniqueId(entity, now);
        return get(publicId);
    }

    private String insertWithUniqueId(ItemPostEntity entity, LocalDateTime now) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String publicId = "LF-" + PUBLIC_ID_DATE.format(now) + "-" + randomSuffix();
            entity.setPublicId(publicId);
            try {
                itemPostMapper.insert(entity);
                return publicId;
            } catch (DuplicateKeyException ex) {
                // 编号冲突时换一个重试
            }
        }
        throw ApiException.conflict("发布编号生成失败，请稍后重试");
    }

    private String randomSuffix() {
        StringBuilder builder = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            int index = ThreadLocalRandom.current().nextInt(PUBLIC_ID_ALPHABET.length());
            builder.append(PUBLIC_ID_ALPHABET.charAt(index));
        }
        return builder.toString();
    }

    private boolean categoryExists(Long categoryId) {
        CategoryEntity category = categoryMapper.selectOne(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getId, categoryId)
                .eq(CategoryEntity::getEnabled, true)
                .last("LIMIT 1"));
        return category != null;
    }

    private Long normalizeLocation(Long locationId) {
        if (locationId == null) {
            return null;
        }
        CampusLocationEntity location = locationMapper.selectOne(Wrappers.<CampusLocationEntity>lambdaQuery()
                .eq(CampusLocationEntity::getId, locationId)
                .eq(CampusLocationEntity::getEnabled, true)
                .last("LIMIT 1"));
        if (location == null) {
            throw ApiException.badRequest("所选地点不存在或已停用");
        }
        return locationId;
    }

    private String normalizeContactMode(String contactMode) {
        if (contactMode == null || contactMode.isBlank()) {
            return "IN_APP_ONLY";
        }
        String normalized = contactMode.trim().toUpperCase(Locale.ROOT);
        if (!CONTACT_MODES.contains(normalized)) {
            throw ApiException.badRequest("contactMode 只允许 PUBLIC、AFTER_CLAIM 或 IN_APP_ONLY");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

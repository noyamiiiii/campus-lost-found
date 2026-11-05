package edu.campus.lostfound.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campus.lostfound.admin.AdminDtos.AdminSummary;
import edu.campus.lostfound.admin.AdminDtos.AdminUserPage;
import edu.campus.lostfound.admin.AdminDtos.AdminUserView;
import edu.campus.lostfound.admin.AdminDtos.UserStatusRequest;
import edu.campus.lostfound.common.ApiException;
import edu.campus.lostfound.common.TraceIdFilter;
import edu.campus.lostfound.post.ItemPostEntity;
import edu.campus.lostfound.post.ItemPostMapper;
import edu.campus.lostfound.user.RefreshTokenEntity;
import edu.campus.lostfound.user.RefreshTokenMapper;
import edu.campus.lostfound.user.SysUserEntity;
import edu.campus.lostfound.user.SysUserMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local")
public class AdminService {

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> EDITABLE_STATUS = Set.of("ACTIVE", "BANNED");
    private static final Set<String> FILTER_STATUS = Set.of("ACTIVE", "BANNED");

    private final SysUserMapper userMapper;
    private final ItemPostMapper postMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final AdminQueryMapper adminQueryMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AdminService(
            SysUserMapper userMapper,
            ItemPostMapper postMapper,
            RefreshTokenMapper refreshTokenMapper,
            AdminQueryMapper adminQueryMapper,
            AdminAuditLogMapper auditLogMapper,
            ObjectMapper objectMapper
    ) {
        this.userMapper = userMapper;
        this.postMapper = postMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.adminQueryMapper = adminQueryMapper;
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminSummary summary() {
        long totalUsers = userMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                .ne(SysUserEntity::getStatus, "DELETED"));
        long activeUsers = userMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getStatus, "ACTIVE"));
        long bannedUsers = userMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getStatus, "BANNED"));
        long adminUsers = userMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                .in(SysUserEntity::getRole, "ADMIN", "SERVICE_ADMIN")
                .ne(SysUserEntity::getStatus, "DELETED"));
        long totalPosts = postMapper.selectCount(Wrappers.<ItemPostEntity>lambdaQuery()
                .isNull(ItemPostEntity::getDeletedAt));
        long activePosts = postMapper.selectCount(Wrappers.<ItemPostEntity>lambdaQuery()
                .isNull(ItemPostEntity::getDeletedAt)
                .eq(ItemPostEntity::getStatus, "ACTIVE"));
        return new AdminSummary(totalUsers, activeUsers, bannedUsers, adminUsers, totalPosts, activePosts);
    }

    @Transactional(readOnly = true)
    public AdminUserPage listUsers(String keyword, String status, int page, int size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeFilterStatus(status);
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(50, Math.max(5, size));

        long total = adminQueryMapper.countUsers(normalizedKeyword, normalizedStatus);
        List<AdminUserRow> rows = adminQueryMapper.findUsers(
                normalizedKeyword,
                normalizedStatus,
                normalizedSize,
                (normalizedPage - 1) * normalizedSize);
        return new AdminUserPage(
                rows.stream().map(this::toView).toList(),
                total,
                normalizedPage,
                normalizedSize);
    }

    @Transactional
    public AdminUserView updateStatus(Long adminUserId, String publicId, UserStatusRequest request) {
        String status = request.status() == null ? "" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!EDITABLE_STATUS.contains(status)) {
            throw ApiException.badRequest("状态只允许 ACTIVE 或 BANNED");
        }
        SysUserEntity user = userMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getPublicId, publicId)
                .ne(SysUserEntity::getStatus, "DELETED")
                .last("LIMIT 1"));
        if (user == null) {
            throw ApiException.notFound("账号不存在");
        }
        if (user.getId().equals(adminUserId)) {
            throw ApiException.badRequest("不能修改自己的账号状态");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw ApiException.forbidden("管理员账号不能在账号管理中封禁");
        }

        String previousStatus = user.getStatus();
        if (!status.equals(previousStatus)) {
            userMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                    .eq(SysUserEntity::getId, user.getId())
                    .set(SysUserEntity::getStatus, status));
            if ("BANNED".equals(status)) {
                refreshTokenMapper.update(null, Wrappers.<RefreshTokenEntity>lambdaUpdate()
                        .eq(RefreshTokenEntity::getUserId, user.getId())
                        .isNull(RefreshTokenEntity::getRevokedAt)
                        .set(RefreshTokenEntity::getRevokedAt, LocalDateTime.now()));
            }
            writeAudit(adminUserId, user.getId(), previousStatus, status, request.reason());
        }
        user.setStatus(status);
        return toView(user);
    }

    private void writeAudit(Long adminId, Long targetId, String before, String after, String reason) {
        auditLogMapper.insert(
                adminId,
                "USER_STATUS_CHANGE",
                "USER",
                targetId,
                toJson(Map.of("status", before)),
                toJson(Map.of("status", after, "reason", reason == null ? "" : reason.trim())),
                MDC.get(TraceIdFilter.TRACE_ID),
                null,
                null);
    }

    private String toJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private AdminUserView toView(AdminUserRow row) {
        long postCount = row.getPostCount() == null ? 0 : row.getPostCount();
        return new AdminUserView(
                row.getPublicId(),
                row.getStudentNo(),
                row.getRealName(),
                maskPhone(row.getPhone()),
                row.getDept(),
                row.getRole(),
                row.getStatus(),
                postCount,
                toOffsetDateTime(row.getCreatedAt()),
                toOffsetDateTime(row.getLastLoginAt()));
    }

    private AdminUserView toView(SysUserEntity user) {
        long postCount = postMapper.selectCount(Wrappers.<ItemPostEntity>lambdaQuery()
                .eq(ItemPostEntity::getUserId, user.getId())
                .isNull(ItemPostEntity::getDeletedAt));
        return new AdminUserView(
                user.getPublicId(),
                user.getStudentNo(),
                user.getRealName(),
                maskPhone(user.getPhone()),
                user.getDept(),
                user.getRole(),
                user.getStatus(),
                postCount,
                toOffsetDateTime(user.getCreatedAt()),
                toOffsetDateTime(user.getLastLoginAt()));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 64) {
            throw ApiException.badRequest("搜索关键词不能超过 64 个字符");
        }
        return normalized;
    }

    private String normalizeFilterStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!FILTER_STATUS.contains(normalized)) {
            throw ApiException.badRequest("status 只允许 ACTIVE 或 BANNED");
        }
        return normalized;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(CAMPUS_ZONE).toOffsetDateTime();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}

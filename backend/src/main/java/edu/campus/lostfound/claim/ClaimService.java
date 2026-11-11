package edu.campus.lostfound.claim;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.campus.lostfound.auth.AuthenticatedUser;
import edu.campus.lostfound.claim.ClaimDtos.ClaimView;
import edu.campus.lostfound.claim.ClaimDtos.CreateRequest;
import edu.campus.lostfound.claim.ClaimDtos.MessageRequest;
import edu.campus.lostfound.common.ApiException;
import edu.campus.lostfound.notification.NotificationService;
import edu.campus.lostfound.post.ItemPostEntity;
import edu.campus.lostfound.post.ItemPostMapper;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认领状态机：PENDING → CONFIRMED → COMPLETED，拒绝后可重开，申请人可随时撤回。
 * 同一帖子同一时间只允许一条进行中的认领（DB 唯一索引兜底）。
 */
@Service
@Profile("local")
public class ClaimService {

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PUBLIC_ID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final SecureRandom RANDOM = new SecureRandom();

    static final String PENDING = "PENDING";
    static final String CONFIRMED = "CONFIRMED";
    static final String REJECTED = "REJECTED";
    static final String CANCELED = "CANCELED";
    static final String COMPLETED = "COMPLETED";

    private final ClaimMapper claimMapper;
    private final ClaimQueryMapper claimQueryMapper;
    private final ClaimStatusHistoryMapper historyMapper;
    private final ItemPostMapper itemPostMapper;
    private final NotificationService notificationService;

    public ClaimService(ClaimMapper claimMapper,
                        ClaimQueryMapper claimQueryMapper,
                        ClaimStatusHistoryMapper historyMapper,
                        ItemPostMapper itemPostMapper,
                        NotificationService notificationService) {
        this.claimMapper = claimMapper;
        this.claimQueryMapper = claimQueryMapper;
        this.historyMapper = historyMapper;
        this.itemPostMapper = itemPostMapper;
        this.notificationService = notificationService;
    }

    @Transactional
    public ClaimView create(Long userId, String postPublicId, CreateRequest request) {
        ItemPostEntity post = requirePost(postPublicId);
        if (Objects.equals(post.getUserId(), userId)) {
            throw ApiException.badRequest("不能认领自己发布的物品");
        }
        if (!"ACTIVE".equals(post.getStatus()) && !"CLAIMING".equals(post.getStatus())) {
            throw ApiException.conflict("该物品当前不可认领（状态：" + post.getStatus() + "）");
        }
        Long active = claimMapper.selectCount(Wrappers.<ClaimEntity>lambdaQuery()
                .eq(ClaimEntity::getPostId, post.getId())
                .in(ClaimEntity::getStatus, List.of(PENDING, CONFIRMED)));
        if (active != null && active > 0) {
            throw ApiException.conflict("该物品已有进行中的认领申请，请等待处理结果");
        }

        LocalDateTime now = LocalDateTime.now(CAMPUS_ZONE);
        ClaimEntity entity = new ClaimEntity();
        entity.setPublicId(nextPublicId(now));
        entity.setPostId(post.getId());
        entity.setApplicantId(userId);
        entity.setStatus(PENDING);
        entity.setMessage(request.message().trim());
        entity.setContact(blankToNull(request.contact()));
        entity.setEvidenceFileId(request.evidenceFileId());
        entity.setVersion(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        claimMapper.insert(entity);
        recordHistory(entity.getId(), null, PENDING, userId, "提交认领");
        markPostClaiming(post);

        notificationService.notify(post.getUserId(), "CLAIM", "有人认领你发布的物品",
                "「" + post.getTitle() + "」收到一条新的认领申请，请及时处理。",
                "CLAIM", entity.getId(), "claim:new:" + entity.getId());
        return toView(claimQueryMapper.findOne(entity.getId()));
    }

    @Transactional(readOnly = true)
    public List<ClaimView> findMine(Long userId) {
        return claimQueryMapper.findMine(userId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimView> findReceived(Long userId) {
        return claimQueryMapper.findReceived(userId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ClaimView get(Long userId, String claimPublicId) {
        ClaimRow row = requireRow(claimPublicId);
        if (!Objects.equals(row.getApplicantId(), userId) && !Objects.equals(row.getOwnerId(), userId)) {
            throw ApiException.forbidden("无权查看该认领");
        }
        return toView(row);
    }

    @Transactional
    public ClaimView confirm(Long userId, String claimPublicId, MessageRequest request) {
        ClaimRow row = requireOwned(row(claimPublicId), userId);
        ClaimEntity claim = requireClaim(row);
        requireStatus(claim, PENDING);
        claim.setStatus(CONFIRMED);
        claim.setConfirmedAt(LocalDateTime.now(CAMPUS_ZONE));
        claim.setVersion(claim.getVersion() + 1);
        claimMapper.updateById(claim);
        recordHistory(claim.getId(), PENDING, CONFIRMED, userId, request.message());

        ItemPostEntity post = itemPostMapper.selectById(claim.getPostId());
        if (post != null && "ACTIVE".equals(post.getStatus())) {
            post.setStatus("CLAIMING");
            itemPostMapper.updateById(post);
        }
        notificationService.notify(claim.getApplicantId(), "CLAIM", "你的认领已被同意",
                "「" + row.getPostTitle() + "」的发布者同意了你的认领，可按留下的联系方式约见交接。",
                "CLAIM", claim.getId(), "claim:confirmed:" + claim.getId());
        return toView(claimQueryMapper.findOne(claim.getId()));
    }

    @Transactional
    public ClaimView reject(Long userId, String claimPublicId, MessageRequest request) {
        ClaimRow row = requireOwned(row(claimPublicId), userId);
        ClaimEntity claim = requireClaim(row);
        requireStatus(claim, PENDING);
        claim.setStatus(REJECTED);
        claim.setVersion(claim.getVersion() + 1);
        claimMapper.updateById(claim);
        recordHistory(claim.getId(), PENDING, REJECTED, userId, request.message());
        restorePostIfIdle(claim);
        notificationService.notify(claim.getApplicantId(), "CLAIM", "你的认领被拒绝",
                "「" + row.getPostTitle() + "」的发布者未通过你的认领申请。原因：" + request.message(),
                "CLAIM", claim.getId(), "claim:rejected:" + claim.getId() + ":" + claim.getVersion());
        return toView(claimQueryMapper.findOne(claim.getId()));
    }

    @Transactional
    public ClaimView reopen(Long userId, String claimPublicId, MessageRequest request) {
        ClaimRow row = requireRow(claimPublicId);
        if (!Objects.equals(row.getApplicantId(), userId)) {
            throw ApiException.forbidden("只有申请人可以重新提交认领");
        }
        ClaimEntity claim = requireClaim(row);
        requireStatus(claim, REJECTED);
        claim.setStatus(PENDING);
        claim.setVersion(claim.getVersion() + 1);
        claimMapper.updateById(claim);
        recordHistory(claim.getId(), REJECTED, PENDING, userId, request.message());
        ItemPostEntity reopenedPost = itemPostMapper.selectById(claim.getPostId());
        if (reopenedPost != null) {
            markPostClaiming(reopenedPost);
        }
        notificationService.notify(row.getOwnerId(), "CLAIM", "认领申请已重新提交",
                "「" + row.getPostTitle() + "」的申请人补充了说明，请重新处理。",
                "CLAIM", claim.getId(), "claim:reopened:" + claim.getId() + ":" + claim.getVersion());
        return toView(claimQueryMapper.findOne(claim.getId()));
    }

    @Transactional
    public ClaimView cancel(Long userId, String claimPublicId) {
        ClaimRow row = requireRow(claimPublicId);
        if (!Objects.equals(row.getApplicantId(), userId)) {
            throw ApiException.forbidden("只有申请人可以撤回认领");
        }
        ClaimEntity claim = requireClaim(row);
        if (!PENDING.equals(claim.getStatus()) && !CONFIRMED.equals(claim.getStatus())) {
            throw ApiException.conflict("当前状态不可撤回");
        }
        String from = claim.getStatus();
        claim.setStatus(CANCELED);
        claim.setCanceledAt(LocalDateTime.now(CAMPUS_ZONE));
        claim.setVersion(claim.getVersion() + 1);
        claimMapper.updateById(claim);
        recordHistory(claim.getId(), from, CANCELED, userId, "申请人撤回");
        restorePostIfIdle(claim);
        notificationService.notify(row.getOwnerId(), "CLAIM", "认领申请已撤回",
                "「" + row.getPostTitle() + "」的申请人撤回了认领。",
                "CLAIM", claim.getId(), "claim:canceled:" + claim.getId() + ":" + claim.getVersion());
        return toView(claimQueryMapper.findOne(claim.getId()));
    }

    @Transactional
    public ClaimView complete(Long userId, String claimPublicId, MessageRequest request) {
        ClaimRow row = requireOwned(row(claimPublicId), userId);
        ClaimEntity claim = requireClaim(row);
        requireStatus(claim, CONFIRMED);
        LocalDateTime now = LocalDateTime.now(CAMPUS_ZONE);
        claim.setStatus(COMPLETED);
        claim.setCompletedAt(now);
        claim.setVersion(claim.getVersion() + 1);
        claimMapper.updateById(claim);
        recordHistory(claim.getId(), CONFIRMED, COMPLETED, userId, request.message());

        ItemPostEntity post = itemPostMapper.selectById(claim.getPostId());
        if (post != null) {
            post.setStatus("CLAIMED");
            post.setReturnedAt(now);
            itemPostMapper.updateById(post);
        }
        notificationService.notify(claim.getApplicantId(), "CLAIM", "物品已确认交接",
                "「" + row.getPostTitle() + "」已完成交接，感谢你帮助物品回到主人身边。",
                "CLAIM", claim.getId(), "claim:completed:" + claim.getId());
        return toView(claimQueryMapper.findOne(claim.getId()));
    }

    private void markPostClaiming(ItemPostEntity post) {
        if (post != null && "ACTIVE".equals(post.getStatus())) {
            post.setStatus("CLAIMING");
            itemPostMapper.updateById(post);
        }
    }

    private void restorePostIfIdle(ClaimEntity claim) {
        ItemPostEntity post = itemPostMapper.selectById(claim.getPostId());
        if (post == null || !"CLAIMING".equals(post.getStatus())) {
            return;
        }
        Long stillConfirmed = claimMapper.selectCount(Wrappers.<ClaimEntity>lambdaQuery()
                .eq(ClaimEntity::getPostId, claim.getPostId())
                .eq(ClaimEntity::getStatus, CONFIRMED));
        if (stillConfirmed == null || stillConfirmed == 0) {
            post.setStatus("ACTIVE");
            itemPostMapper.updateById(post);
        }
    }

    private ClaimEntity requireClaim(ClaimRow row) {
        ClaimEntity claim = claimMapper.selectById(row.getId());
        if (claim == null) {
            throw ApiException.notFound("认领记录不存在");
        }
        return claim;
    }

    private ClaimRow requireRow(String claimPublicId) {
        ClaimEntity entity = claimMapper.selectOne(Wrappers.<ClaimEntity>lambdaQuery()
                .eq(ClaimEntity::getPublicId, claimPublicId));
        if (entity == null) {
            throw ApiException.notFound("认领记录不存在");
        }
        return claimQueryMapper.findOne(entity.getId());
    }

    private ClaimRow row(String claimPublicId) {
        return requireRow(claimPublicId);
    }

    private ClaimRow requireOwned(ClaimRow row, Long userId) {
        if (!Objects.equals(row.getOwnerId(), userId)) {
            throw ApiException.forbidden("只有发布者可以处理申请");
        }
        return row;
    }

    private void requireStatus(ClaimEntity claim, String expected) {
        if (!expected.equals(claim.getStatus())) {
            throw ApiException.conflict("状态已变化，请刷新后重试（当前：" + claim.getStatus() + "）");
        }
    }

    private ItemPostEntity requirePost(String postPublicId) {
        ItemPostEntity post = itemPostMapper.selectOne(Wrappers.<ItemPostEntity>lambdaQuery()
                .eq(ItemPostEntity::getPublicId, postPublicId));
        if (post == null || post.getDeletedAt() != null) {
            throw ApiException.notFound("帖子不存在");
        }
        return post;
    }

    private void recordHistory(Long claimId, String from, String to, Long actorId, String reason) {
        ClaimStatusHistoryEntity history = new ClaimStatusHistoryEntity();
        history.setClaimId(claimId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setActorId(actorId);
        history.setReason(reason == null ? null : reason.trim());
        history.setCreatedAt(LocalDateTime.now(CAMPUS_ZONE));
        historyMapper.insert(history);
    }

    private String nextPublicId(LocalDateTime now) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "CL-" + PUBLIC_ID_DATE.format(now) + "-"
                    + String.format("%04X", RANDOM.nextInt(0x10000));
            Long exists = claimMapper.selectCount(Wrappers.<ClaimEntity>lambdaQuery()
                    .eq(ClaimEntity::getPublicId, candidate));
            if (exists == null || exists == 0) {
                return candidate;
            }
        }
        throw new DuplicateKeyException("无法生成唯一的认领编号");
    }

    private ClaimView toView(ClaimRow row) {
        return new ClaimView(
                row.getPublicId(),
                row.getPostPublicId(),
                row.getPostTitle(),
                row.getPostStatus(),
                row.getApplicantPublicId(),
                row.getApplicantName(),
                row.getOwnerPublicId(),
                row.getOwnerName(),
                row.getStatus(),
                row.getMessage(),
                row.getContact(),
                row.getEvidenceFileId(),
                toOffset(row.getConfirmedAt()),
                toOffset(row.getCompletedAt()),
                toOffset(row.getCanceledAt()),
                toOffset(row.getCreatedAt()));
    }

    private OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(CAMPUS_ZONE).toOffsetDateTime();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
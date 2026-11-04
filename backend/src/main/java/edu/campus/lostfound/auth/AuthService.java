package edu.campus.lostfound.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.campus.lostfound.auth.AuthDtos.AuthSession;
import edu.campus.lostfound.auth.AuthDtos.IssuedSession;
import edu.campus.lostfound.common.ApiException;
import edu.campus.lostfound.user.RefreshTokenEntity;
import edu.campus.lostfound.user.RefreshTokenMapper;
import edu.campus.lostfound.user.SysUserEntity;
import edu.campus.lostfound.user.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local")
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SmsCodeService smsCodeService;
    private final UserService userService;
    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtService jwtService;
    private final AuthProperties properties;

    public AuthService(
            SmsCodeService smsCodeService,
            UserService userService,
            RefreshTokenMapper refreshTokenMapper,
            JwtService jwtService,
            AuthProperties properties
    ) {
        this.smsCodeService = smsCodeService;
        this.userService = userService;
        this.refreshTokenMapper = refreshTokenMapper;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    public AuthDtos.SendCodeData sendCode(String studentNo, String phone) {
        return smsCodeService.send(studentNo.trim(), phone.trim());
    }

    @Transactional
    public IssuedSession login(String studentNo, String phone, String code) {
        String normalizedStudentNo = studentNo.trim();
        String normalizedPhone = phone.trim();
        smsCodeService.verify(normalizedStudentNo, normalizedPhone, code);

        SysUserEntity user = userService.findByStudentNo(normalizedStudentNo);
        if (user == null) {
            throw ApiException.unauthorized("账号不存在，请先注册");
        }
        if (!normalizedPhone.equals(user.getPhone())) {
            throw ApiException.unauthorized("学号与手机号不匹配");
        }

        ensureActive(user);
        userService.updateLastLogin(user.getId());
        return issueSession(user);
    }

    @Transactional
    public IssuedSession register(String studentNo, String phone, String code) {
        String normalizedStudentNo = studentNo.trim();
        String normalizedPhone = phone.trim();

        if (userService.findByStudentNo(normalizedStudentNo) != null) {
            throw ApiException.conflict("该学号已注册，请直接登录");
        }
        if (userService.findByPhone(normalizedPhone) != null) {
            throw ApiException.conflict("该手机号已注册，请直接登录");
        }

        smsCodeService.verify(normalizedStudentNo, normalizedPhone, code);
        SysUserEntity user = userService.createStudent(normalizedStudentNo, normalizedPhone);
        return issueSession(user);
    }

    @Transactional
    public IssuedSession refresh(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录");
        }
        String tokenHash = hash(rawRefreshToken);
        RefreshTokenEntity stored = refreshTokenMapper.selectOne(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getTokenHash, tokenHash)
                .isNull(RefreshTokenEntity::getRevokedAt)
                .gt(RefreshTokenEntity::getExpiresAt, LocalDateTime.now())
                .last("LIMIT 1"));
        if (stored == null) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录");
        }

        SysUserEntity user = userService.requireById(stored.getUserId());
        ensureActive(user);
        refreshTokenMapper.update(null, Wrappers.<RefreshTokenEntity>lambdaUpdate()
                .eq(RefreshTokenEntity::getId, stored.getId())
                .set(RefreshTokenEntity::getRevokedAt, LocalDateTime.now()));
        return issueSession(user);
    }

    private void ensureActive(SysUserEntity user) {
        if (!"ACTIVE".equals(user.getStatus())) {
            throw ApiException.forbidden("账号当前不可登录");
        }
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            return;
        }
        refreshTokenMapper.update(null, Wrappers.<RefreshTokenEntity>lambdaUpdate()
                .eq(RefreshTokenEntity::getTokenHash, hash(rawRefreshToken))
                .isNull(RefreshTokenEntity::getRevokedAt)
                .set(RefreshTokenEntity::getRevokedAt, LocalDateTime.now()));
    }

    private IssuedSession issueSession(SysUserEntity user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getPublicId(), user.getRole());
        String refreshToken = generateRefreshToken();
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUserId(user.getId());
        entity.setTokenHash(hash(refreshToken));
        entity.setExpiresAt(LocalDateTime.now().plus(properties.getRefreshTokenTtl()));
        entity.setDevice("web");
        refreshTokenMapper.insert(entity);

        var profile = userService.toView(user);
        AuthSession session = new AuthSession(
                accessToken,
                "Bearer",
                properties.getAccessTokenTtl().toSeconds(),
                profile,
                !profile.profileComplete());
        return new IssuedSession(session, refreshToken);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}

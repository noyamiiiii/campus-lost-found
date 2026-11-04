package edu.campus.lostfound.auth;

import edu.campus.lostfound.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class SmsCodeService {

    private static final int CODE_TTL_SECONDS = 300;
    private static final int SEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;

    private final AuthProperties properties;
    private final ConcurrentHashMap<String, CodeEntry> codes = new ConcurrentHashMap<>();

    public SmsCodeService(AuthProperties properties) {
        this.properties = properties;
    }

    public AuthDtos.SendCodeData send(String studentNo, String phone) {
        String key = key(studentNo, phone);
        Instant now = Instant.now();
        CodeEntry previous = codes.get(key);
        if (previous != null && now.isBefore(previous.sentAt().plusSeconds(SEND_COOLDOWN_SECONDS))) {
            long retryAfter = Math.max(1, SEND_COOLDOWN_SECONDS - (now.getEpochSecond() - previous.sentAt().getEpochSecond()));
            throw ApiException.tooManyRequests("验证码发送过于频繁，请在 " + retryAfter + " 秒后重试");
        }
        codes.put(key, new CodeEntry(properties.getFixedSmsCode(), now, now.plusSeconds(CODE_TTL_SECONDS), new AtomicInteger()));
        String debugCode = properties.isExposeSmsCode() ? properties.getFixedSmsCode() : null;
        return new AuthDtos.SendCodeData(CODE_TTL_SECONDS, SEND_COOLDOWN_SECONDS, debugCode);
    }

    public void verify(String studentNo, String phone, String submittedCode) {
        String key = key(studentNo, phone);
        CodeEntry entry = codes.get(key);
        Instant now = Instant.now();
        if (entry == null || !now.isBefore(entry.expiresAt())) {
            codes.remove(key);
            throw ApiException.unauthorized("验证码无效或已过期，请重新获取");
        }
        int attempts = entry.failedAttempts().incrementAndGet();
        boolean matches = MessageDigest.isEqual(
                entry.code().getBytes(StandardCharsets.UTF_8),
                submittedCode.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            if (attempts >= MAX_ATTEMPTS) {
                codes.remove(key);
            }
            throw ApiException.unauthorized("验证码错误，请检查后重试");
        }
        codes.remove(key);
    }

    private String key(String studentNo, String phone) {
        return studentNo + "|" + phone;
    }

    private record CodeEntry(String code, Instant sentAt, Instant expiresAt, AtomicInteger failedAttempts) {
    }
}

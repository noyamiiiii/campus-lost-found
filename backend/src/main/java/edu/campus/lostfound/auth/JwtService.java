package edu.campus.lostfound.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final AuthProperties properties;
    private final SecretKey signingKey;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
        byte[] secret = properties.getJwtSecret() == null
                ? new byte[0]
                : properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("AUTH_JWT_SECRET 至少需要 32 字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
    }

    public String issueAccessToken(Long userId, String publicId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(publicId)
                .claim("uid", userId)
                .claim("role", role)
                .claim("type", "access")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(signingKey)
                .compact();
    }

    public Optional<AuthenticatedUser> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!"access".equals(claims.get("type", String.class))) {
                return Optional.empty();
            }
            Number userId = claims.get("uid", Number.class);
            String publicId = claims.getSubject();
            String role = claims.get("role", String.class);
            if (userId == null || publicId == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(userId.longValue(), publicId, role));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}

package edu.campus.lostfound.auth;

import edu.campus.lostfound.auth.AuthDtos.AuthSession;
import edu.campus.lostfound.auth.AuthDtos.LoginRequest;
import edu.campus.lostfound.auth.AuthDtos.SendCodeData;
import edu.campus.lostfound.auth.AuthDtos.SendCodeRequest;
import edu.campus.lostfound.common.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("local")
public class AuthController {

    private final AuthService authService;
    private final AuthProperties properties;

    public AuthController(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/sms-code")
    public ApiResponse<SendCodeData> sendCode(@Valid @RequestBody SendCodeRequest request) {
        return ApiResponse.ok(authService.sendCode(request.studentNo(), request.phone()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthSession>> login(@Valid @RequestBody LoginRequest request) {
        var issued = authService.login(request.studentNo(), request.phone(), request.code());
        return sessionResponse(issued.session(), issued.refreshToken());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthSession>> refresh(HttpServletRequest request) {
        var issued = authService.refresh(readRefreshToken(request));
        return sessionResponse(issued.session(), issued.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        authService.logout(readRefreshToken(request));
        ResponseCookie cookie = ResponseCookie.from(properties.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(properties.isRefreshCookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.<Void>ok(null));
    }

    private ResponseEntity<ApiResponse<AuthSession>> sessionResponse(AuthSession session, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(properties.getRefreshCookieName(), refreshToken)
                .httpOnly(true)
                .secure(properties.isRefreshCookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(properties.getRefreshTokenTtl())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(session));
    }

    private String readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (properties.getRefreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

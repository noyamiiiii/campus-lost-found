package edu.campus.lostfound.auth;

import edu.campus.lostfound.user.UserDtos.UserProfileView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SendCodeRequest(
            @NotBlank(message = "学号不能为空")
            @Pattern(regexp = "^[A-Za-z0-9]{4,24}$", message = "学号应为 4 到 24 位字母或数字")
            String studentNo,
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1[3-9][0-9]{9}$", message = "请输入有效的 11 位手机号")
            String phone
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "学号不能为空")
            @Pattern(regexp = "^[A-Za-z0-9]{4,24}$", message = "学号应为 4 到 24 位字母或数字")
            String studentNo,
            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1[3-9][0-9]{9}$", message = "请输入有效的 11 位手机号")
            String phone,
            @NotBlank(message = "验证码不能为空")
            @Pattern(regexp = "^[0-9]{6}$", message = "验证码应为 6 位数字")
            String code
    ) {
    }

    public record SendCodeData(int expiresInSeconds, int retryAfterSeconds, String debugCode) {
    }

    public record AuthSession(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserProfileView user,
            boolean needsProfileCompletion
    ) {
    }

    public record IssuedSession(AuthSession session, String refreshToken) {
    }
}

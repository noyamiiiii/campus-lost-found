package edu.campus.lostfound.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserProfileView(
            String publicId,
            String studentNo,
            String realName,
            String phoneMasked,
            String dept,
            String dorm,
            String wechat,
            String role,
            String status,
            boolean profileComplete,
            boolean phoneVerified
    ) {
    }

    public record UserProfileUpdateRequest(
            @NotBlank(message = "姓名不能为空")
            @Size(max = 64, message = "姓名不能超过 64 个字符")
            String realName,
            @NotBlank(message = "院系不能为空")
            @Size(max = 64, message = "院系不能超过 64 个字符")
            String dept,
            @Size(max = 64, message = "宿舍不能超过 64 个字符")
            String dorm,
            @Size(max = 64, message = "微信号不能超过 64 个字符")
            String wechat
    ) {
    }
}

package edu.campus.lostfound.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminSummary(
            long totalUsers,
            long activeUsers,
            long bannedUsers,
            long adminUsers,
            long totalPosts,
            long activePosts
    ) {
    }

    public record AdminUserView(
            String publicId,
            String studentNo,
            String realName,
            String phoneMasked,
            String dept,
            String role,
            String status,
            long postCount,
            OffsetDateTime createdAt,
            OffsetDateTime lastLoginAt
    ) {
    }

    public record AdminUserPage(List<AdminUserView> list, long total, int page, int size) {
    }

    public record UserStatusRequest(
            @NotBlank(message = "请选择要设置的状态")
            String status,
            @Size(max = 200, message = "备注不能超过 200 个字符")
            String reason
    ) {
    }
}

package edu.campus.lostfound.admin;

import edu.campus.lostfound.admin.AdminDtos.AdminSummary;
import edu.campus.lostfound.admin.AdminDtos.AdminUserPage;
import edu.campus.lostfound.admin.AdminDtos.AdminUserView;
import edu.campus.lostfound.admin.AdminDtos.UserStatusRequest;
import edu.campus.lostfound.auth.AuthenticatedUser;
import edu.campus.lostfound.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Profile("local")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/summary")
    public ApiResponse<AdminSummary> summary() {
        return ApiResponse.ok(adminService.summary());
    }

    @GetMapping("/users")
    public ApiResponse<AdminUserPage> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(adminService.listUsers(keyword, status, page, size));
    }

    @PatchMapping("/users/{publicId}/status")
    public ApiResponse<AdminUserView> updateStatus(
            @PathVariable String publicId,
            @Valid @RequestBody UserStatusRequest request,
            Authentication authentication
    ) {
        AuthenticatedUser admin = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.ok(adminService.updateStatus(admin.userId(), publicId, request));
    }
}

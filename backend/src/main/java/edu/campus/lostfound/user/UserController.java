package edu.campus.lostfound.user;

import edu.campus.lostfound.auth.AuthenticatedUser;
import edu.campus.lostfound.common.ApiResponse;
import edu.campus.lostfound.user.UserDtos.UserProfileUpdateRequest;
import edu.campus.lostfound.user.UserDtos.UserProfileView;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Profile("local")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileView> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(userService.getProfile(user.userId()));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileView> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        return ApiResponse.ok(userService.updateProfile(user.userId(), request));
    }
}

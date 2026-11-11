package edu.campus.lostfound.notification;

import edu.campus.lostfound.auth.AuthenticatedUser;
import edu.campus.lostfound.common.ApiResponse;
import edu.campus.lostfound.notification.NotificationDtos.NotificationView;
import edu.campus.lostfound.notification.NotificationDtos.UnreadCountData;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Profile("local")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotificationView>> list(Authentication authentication) {
        return ApiResponse.ok(notificationService.list(currentUser(authentication).userId()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountData> unreadCount(Authentication authentication) {
        return ApiResponse.ok(new UnreadCountData(notificationService.unreadCount(currentUser(authentication).userId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id, Authentication authentication) {
        notificationService.markRead(currentUser(authentication).userId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(Authentication authentication) {
        notificationService.markAllRead(currentUser(authentication).userId());
        return ApiResponse.ok(null);
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
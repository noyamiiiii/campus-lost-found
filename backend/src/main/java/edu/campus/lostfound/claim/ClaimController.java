package edu.campus.lostfound.claim;

import edu.campus.lostfound.auth.AuthenticatedUser;
import edu.campus.lostfound.claim.ClaimDtos.ClaimView;
import edu.campus.lostfound.claim.ClaimDtos.CreateRequest;
import edu.campus.lostfound.claim.ClaimDtos.MessageRequest;
import edu.campus.lostfound.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Profile("local")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping("/posts/{publicId}/claims")
    public ApiResponse<ClaimView> create(@PathVariable String publicId,
                                         @Valid @RequestBody CreateRequest request,
                                         Authentication authentication) {
        return ApiResponse.ok(claimService.create(currentUser(authentication).userId(), publicId, request));
    }

    @GetMapping("/claims/mine")
    public ApiResponse<List<ClaimView>> mine(Authentication authentication) {
        return ApiResponse.ok(claimService.findMine(currentUser(authentication).userId()));
    }

    @GetMapping("/claims/received")
    public ApiResponse<List<ClaimView>> received(Authentication authentication) {
        return ApiResponse.ok(claimService.findReceived(currentUser(authentication).userId()));
    }

    @GetMapping("/claims/{publicId}")
    public ApiResponse<ClaimView> get(@PathVariable String publicId, Authentication authentication) {
        return ApiResponse.ok(claimService.get(currentUser(authentication).userId(), publicId));
    }

    @PostMapping("/claims/{publicId}/confirm")
    public ApiResponse<ClaimView> confirm(@PathVariable String publicId,
                                          @Valid @RequestBody MessageRequest request,
                                          Authentication authentication) {
        return ApiResponse.ok(claimService.confirm(currentUser(authentication).userId(), publicId, request));
    }

    @PostMapping("/claims/{publicId}/reject")
    public ApiResponse<ClaimView> reject(@PathVariable String publicId,
                                         @Valid @RequestBody MessageRequest request,
                                         Authentication authentication) {
        return ApiResponse.ok(claimService.reject(currentUser(authentication).userId(), publicId, request));
    }

    @PostMapping("/claims/{publicId}/reopen")
    public ApiResponse<ClaimView> reopen(@PathVariable String publicId,
                                         @Valid @RequestBody MessageRequest request,
                                         Authentication authentication) {
        return ApiResponse.ok(claimService.reopen(currentUser(authentication).userId(), publicId, request));
    }

    @PostMapping("/claims/{publicId}/cancel")
    public ApiResponse<ClaimView> cancel(@PathVariable String publicId, Authentication authentication) {
        return ApiResponse.ok(claimService.cancel(currentUser(authentication).userId(), publicId));
    }

    @PostMapping("/claims/{publicId}/complete")
    public ApiResponse<ClaimView> complete(@PathVariable String publicId,
                                           @Valid @RequestBody MessageRequest request,
                                           Authentication authentication) {
        return ApiResponse.ok(claimService.complete(currentUser(authentication).userId(), publicId, request));
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
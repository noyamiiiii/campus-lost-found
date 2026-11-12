package edu.campus.lostfound.post;

import edu.campus.lostfound.auth.AuthenticatedUser;
import edu.campus.lostfound.common.ApiResponse;
import edu.campus.lostfound.post.PostDtos.PostCreateRequest;
import edu.campus.lostfound.post.PostDtos.PostDetail;
import edu.campus.lostfound.post.PostDtos.PostMatch;
import edu.campus.lostfound.post.PostDtos.PostPage;
import edu.campus.lostfound.post.PostDtos.PostSummary;
import edu.campus.lostfound.post.PostDtos.VisualMatch;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@Profile("local")
public class PostController {

    private final PostService postService;
    private final PostMatchService postMatchService;
    private final PostImageSearchService postImageSearchService;

    public PostController(
            PostService postService,
            PostMatchService postMatchService,
            PostImageSearchService postImageSearchService
    ) {
        this.postService = postService;
        this.postMatchService = postMatchService;
        this.postImageSearchService = postImageSearchService;
    }

    @GetMapping
    public ApiResponse<PostPage> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(postService.list(type, categoryId, keyword, cursor, size));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<PostDetail> get(@PathVariable String publicId) {
        return ApiResponse.ok(postService.get(publicId));
    }

    @GetMapping("/{publicId}/matches")
    public ApiResponse<List<PostMatch>> matches(@PathVariable String publicId) {
        return ApiResponse.ok(postMatchService.findMatches(publicId));
    }

    /** 以图搜图（AI-05）：用这条帖子的主图向量找外观相似的帖子。 */
    @GetMapping("/{publicId}/similar-images")
    public ApiResponse<List<VisualMatch>> similarImages(
            @PathVariable String publicId,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ApiResponse.ok(postImageSearchService.searchByPublicId(publicId, limit));
    }

    @GetMapping("/mine")
    public ApiResponse<List<PostSummary>> mine(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.ok(postService.listMine(user.userId()));
    }

    @PostMapping
    public ApiResponse<PostDetail> create(
            @Valid @RequestBody PostCreateRequest request,
            Authentication authentication
    ) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.ok(postService.create(user.userId(), request));
    }
}

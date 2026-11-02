package edu.campus.lostfound.post;

import edu.campus.lostfound.common.ApiResponse;
import edu.campus.lostfound.post.PostDtos.PostDetail;
import edu.campus.lostfound.post.PostDtos.PostPage;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@Profile("local")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
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
}

package edu.campus.lostfound.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class PostDtos {

    private PostDtos() {
    }

    public record PostImage(String url, Integer width, Integer height, boolean primary) {
    }

    public record PostSummary(
            String publicId,
            String type,
            String title,
            String description,
            Long categoryId,
            String categoryName,
            String locationName,
            OffsetDateTime eventTime,
            String status,
            String auditStatus,
            Integer matchCount,
            String publisherName,
            List<PostImage> images
    ) {
    }

    public record PostDetail(
            String publicId,
            String type,
            String title,
            String description,
            Long categoryId,
            String categoryName,
            String locationName,
            String locationDetail,
            String brand,
            String color,
            OffsetDateTime eventTime,
            String status,
            String auditStatus,
            String contactMode,
            Integer matchCount,
            String publisherName,
            OffsetDateTime expireAt,
            List<PostImage> images
    ) {
    }

    public record PostPage(List<PostSummary> list, String nextCursor, boolean hasMore) {
    }

    /** 候选匹配：score 越大越像，reasons 说明为什么被判定为候选。 */
    public record PostMatch(
            String publicId,
            String title,
            String type,
            String categoryName,
            String locationName,
            String status,
            int score,
            List<String> reasons
    ) {
    }

    public record PostCreateRequest(
            @NotBlank(message = "请选择寻物或招领")
            String type,
            @NotBlank(message = "请填写标题")
            @Size(min = 2, max = 60, message = "标题需要 2 到 60 个字符")
            String title,
            @NotBlank(message = "请填写物品描述")
            @Size(min = 5, max = 1000, message = "描述需要 5 到 1000 个字符")
            String description,
            @NotNull(message = "请选择物品分类")
            Long categoryId,
            Long locationId,
            @Size(max = 200, message = "位置补充说明不能超过 200 个字符")
            String detail,
            @NotNull(message = "请选择发生时间")
            OffsetDateTime eventTime,
            @Size(max = 64, message = "品牌不能超过 64 个字符")
            String brand,
            @Size(max = 16, message = "颜色不能超过 16 个字符")
            String color,
            String contactMode,
            @Size(max = 6, message = "最多上传 6 张图片")
            List<Long> imageIds
    ) {
    }
}
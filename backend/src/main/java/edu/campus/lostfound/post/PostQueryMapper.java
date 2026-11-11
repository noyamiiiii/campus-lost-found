package edu.campus.lostfound.post;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("local")
public interface PostQueryMapper {

    List<PostRow> findPage(
            @Param("type") String type,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit
    );

    PostRow findByPublicId(@Param("publicId") String publicId);

    List<PostRow> findMine(@Param("userId") Long userId, @Param("limit") int limit);

    List<PostRow> findMatchCandidates(
            @Param("type") String type,
            @Param("excludeId") Long excludeId,
            @Param("limit") int limit
    );

    List<PostImageRow> findImagesByPostIds(@Param("postIds") List<Long> postIds);
}
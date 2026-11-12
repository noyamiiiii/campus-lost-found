package edu.campus.lostfound.post;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.context.annotation.Profile;

/** 以图搜图用的向量查询：只读 item_image.embedding。 */
@Mapper
@Profile("local")
public interface VisualSearchMapper {

    @Select("""
            SELECT post_id, embedding
            FROM item_image
            WHERE post_id = #{postId} AND embedding IS NOT NULL
            ORDER BY is_primary DESC, id ASC
            LIMIT 1
            """)
    EmbeddingRow findTarget(@Param("postId") Long postId);

    @Select("""
            SELECT post_id, embedding
            FROM item_image
            WHERE embedding IS NOT NULL AND post_id <> #{postId}
            ORDER BY id DESC
            LIMIT 800
            """)
    List<EmbeddingRow> findCandidates(@Param("postId") Long postId);
}
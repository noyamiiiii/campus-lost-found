package edu.campus.lostfound.post;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.campus.lostfound.common.ApiException;
import edu.campus.lostfound.file.FileService;
import edu.campus.lostfound.post.PostDtos.VisualMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 以图搜图（AI-05）：拿帖子主图的向量和旁库里的候选向量算余弦相似度。
 *
 * <p>向量是 mobilenetv2 的 1000 维 L2 归一化输出，点积即相似度。候选集直接从
 * item_image 取，最多 800 条，够一个校园规模的演示；规模再大就应该换向量索引。
 */
@Service
@Profile("local")
public class PostImageSearchService {

    private static final Logger log = LoggerFactory.getLogger(PostImageSearchService.class);

    /** 低于这个分数基本是「都是白墙」这种噪声，不回给前端。 */
    private static final double MIN_SCORE = 0.55;

    private final VisualSearchMapper visualSearchMapper;
    private final PostQueryMapper postQueryMapper;
    private final ItemPostMapper itemPostMapper;
    private final FileService fileService;

    public PostImageSearchService(
            VisualSearchMapper visualSearchMapper,
            PostQueryMapper postQueryMapper,
            ItemPostMapper itemPostMapper,
            FileService fileService
    ) {
        this.visualSearchMapper = visualSearchMapper;
        this.postQueryMapper = postQueryMapper;
        this.itemPostMapper = itemPostMapper;
        this.fileService = fileService;
    }

    @Transactional(readOnly = true)
    public List<VisualMatch> searchByPublicId(String publicId, int limit) {
        ItemPostEntity post = itemPostMapper.selectOne(Wrappers.<ItemPostEntity>lambdaQuery()
                .eq(ItemPostEntity::getPublicId, publicId)
                .last("LIMIT 1"));
        if (post == null) {
            throw ApiException.notFound("帖子不存在或已删除");
        }
        return search(post.getId(), limit);
    }

    @Transactional(readOnly = true)
    public List<VisualMatch> search(Long postId, int limit) {
        int size = Math.min(Math.max(limit, 1), 10);
        EmbeddingRow target = visualSearchMapper.findTarget(postId);
        if (target == null || target.getEmbedding() == null) {
            return List.of();
        }
        float[] query = toFloats(target.getEmbedding());

        Map<Long, Double> best = new HashMap<>();
        for (EmbeddingRow candidate : visualSearchMapper.findCandidates(postId)) {
            float[] vector = toFloats(candidate.getEmbedding());
            if (vector.length != query.length) {
                continue;
            }
            double score = dot(query, vector);
            if (score < MIN_SCORE) {
                continue;
            }
            best.merge(candidate.getPostId(), score, Math::max);
        }
        if (best.isEmpty()) {
            return List.of();
        }

        List<Map.Entry<Long, Double>> ranked = new ArrayList<>(best.entrySet());
        ranked.sort(Comparator.comparingDouble((Map.Entry<Long, Double> e) -> e.getValue()).reversed());
        List<Long> postIds = ranked.stream().limit(size).map(Map.Entry::getKey).toList();

        Map<Long, ItemPostEntity> posts = new LinkedHashMap<>();
        for (ItemPostEntity entity : itemPostMapper.selectBatchIds(postIds)) {
            posts.put(entity.getId(), entity);
        }
        Map<Long, String> covers = new HashMap<>();
        for (PostImageRow image : postQueryMapper.findImagesByPostIds(postIds)) {
            if (!covers.containsKey(image.getPostId()) && Boolean.TRUE.equals(image.getIsPrimary())) {
                covers.put(image.getPostId(), fileService.urlOfKey(image.getStorageKey()));
            }
        }

        List<VisualMatch> result = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : ranked) {
            ItemPostEntity entity = posts.get(entry.getKey());
            if (entity == null || result.size() >= size) {
                continue;
            }
            result.add(new VisualMatch(
                    entity.getPublicId(),
                    entity.getTitle(),
                    entity.getStatus(),
                    covers.get(entity.getId()),
                    Math.round(entry.getValue() * 1000d) / 1000d));
        }
        log.debug("以图搜图 postId={} 命中 {} 条", postId, result.size());
        return result;
    }

    /** 库里存的是大端 float32，两个向量长度不一致说明模型换过，直接判为不可比。 */
    private static float[] toFloats(byte[] bytes) {
        float[] values = new float[bytes.length / 4];
        for (int i = 0; i < values.length; i++) {
            int bits = ((bytes[i * 4] & 0xFF) << 24)
                    | ((bytes[i * 4 + 1] & 0xFF) << 16)
                    | ((bytes[i * 4 + 2] & 0xFF) << 8)
                    | (bytes[i * 4 + 3] & 0xFF);
            values[i] = Float.intBitsToFloat(bits);
        }
        return values;
    }

    private static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }
}
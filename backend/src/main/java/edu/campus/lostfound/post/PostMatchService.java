package edu.campus.lostfound.post;

import edu.campus.lostfound.ai.AiTextClient;
import edu.campus.lostfound.common.ApiException;
import edu.campus.lostfound.post.PostDtos.PostMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 简单的失物/招领候选匹配：先按类别、地点、品牌、颜色等结构化字段加权，
 * 再用中文二元组（bigram）与英文单词计算描述重合度。
 *
 * <p>类别、地点等结构化字段在 Java 侧直接加权；描述相似度优先交给 ai-service 的
 * {@code POST /similar}（词元 + 后续可替换为 embedding），服务不可用时退回本地
 * 中文二元组计算，因此单独起后端也能完整跑通。</p>
 */
@Service
@Profile("local")
public class PostMatchService {

    private static final int CANDIDATE_LIMIT = 80;
    private static final int RESULT_LIMIT = 5;
    private static final Pattern TOKEN = Pattern.compile("[\\u4e00-\\u9fa5]+|[A-Za-z0-9]+");

    /**
     * 量词与叙述性双字词几乎出现在每条记录里（“一张校园卡”“捡到一副”），
     * 留着重合度会被这些词拉平，因此直接从词元集合里剔除。
     */
    private static final Set<String> NOISE = Set.of(
            "一张", "一把", "一副", "一个", "一条", "一只", "一件", "一枚",
            "捡到", "捡了", "拾到", "丢失", "丢了", "失物", "招领", "寻找",
            "校园", "同学", "上面", "下面", "里面", "我的", "这是", "已经", "可以", "有一");

    private final PostQueryMapper postQueryMapper;
    private final AiTextClient aiTextClient;

    public PostMatchService(PostQueryMapper postQueryMapper, AiTextClient aiTextClient) {
        this.postQueryMapper = postQueryMapper;
        this.aiTextClient = aiTextClient;
    }

    public List<PostMatch> findMatches(String publicId) {
        PostRow source = postQueryMapper.findByPublicId(publicId);
        if (source == null) {
            throw ApiException.notFound("记录不存在或已下线");
        }

        String oppositeType = "LOST".equals(source.getType()) ? "FOUND" : "LOST";
        List<PostRow> candidates = postQueryMapper.findMatchCandidates(oppositeType, source.getId(), CANDIDATE_LIMIT);
        Set<String> sourceTokens = tokensOf(source);

        // 语义相似度交给 ai-service；调用失败时 rank 返回空 Map，下面的循环退回二元组重合。
        Map<String, String> candidateTexts = new LinkedHashMap<>();
        for (PostRow candidate : candidates) {
            candidateTexts.put(candidate.getPublicId(), textOf(candidate));
        }
        Map<String, Double> semanticScores = aiTextClient.rank(textOf(source), candidateTexts, CANDIDATE_LIMIT);

        List<PostMatch> matches = new ArrayList<>();
        for (PostRow candidate : candidates) {
            List<String> reasons = new ArrayList<>();
            int score = 0;

            if (Objects.equals(source.getCategoryId(), candidate.getCategoryId())) {
                score += 3;
                reasons.add("同类别 · " + safe(candidate.getCategoryName()));
            }
            if (source.getLocationName() != null && source.getLocationName().equals(candidate.getLocationName())) {
                score += 2;
                reasons.add("同一地点 · " + candidate.getLocationName());
            }
            if (notBlank(source.getBrand()) && source.getBrand().equalsIgnoreCase(candidate.getBrand())) {
                score += 1;
                reasons.add("同品牌 · " + candidate.getBrand());
            }
            if (notBlank(source.getColor()) && source.getColor().equalsIgnoreCase(candidate.getColor())) {
                score += 1;
                reasons.add("同颜色 · " + candidate.getColor());
            }

            Double semantic = semanticScores.get(candidate.getPublicId());
            if (semantic != null && semantic > 0.05) {
                // 相似度 0~1 映射成 0~4 分，压过“同颜色”这类弱特征，但不至于盖过同类别 + 同地点。
                score += (int) Math.round(semantic * 4);
                reasons.add("语义相似 " + Math.round(semantic * 100) + "%");
            } else {
                Set<String> shared = new LinkedHashSet<>(sourceTokens);
                shared.retainAll(tokensOf(candidate));
                if (!shared.isEmpty()) {
                    int weight = Math.min(shared.size(), 3);
                    score += weight * 2;
                    reasons.add("描述重合：" + String.join("、", shared.stream().limit(3).toList()));
                }
            }

            if (score > 0) {
                matches.add(new PostMatch(
                        candidate.getPublicId(),
                        candidate.getTitle(),
                        candidate.getType(),
                        candidate.getCategoryName(),
                        candidate.getLocationName(),
                        candidate.getStatus(),
                        score,
                        List.copyOf(reasons)));
            }
        }

        matches.sort(Comparator.comparingInt(PostMatch::score).reversed());
        return matches.stream().limit(RESULT_LIMIT).toList();
    }

    private Set<String> tokensOf(PostRow row) {
        return tokenize(String.join(" ",
                safe(row.getTitle()),
                safe(row.getDescription()),
                safe(row.getBrand()),
                safe(row.getColor()),
                safe(row.getLocationDetail())));
    }

    /** 交给 ai-service 的文本：标题 + 描述即可，品牌颜色已经作为结构化特征单独加了分。 */
    private String textOf(PostRow row) {
        return (safe(row.getTitle()) + " " + safe(row.getDescription())).trim();
    }

    static Set<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String piece = matcher.group();
            if (piece.charAt(0) < 0x4e00) {
                if (piece.length() > 1) {
                    tokens.add(piece.toLowerCase(Locale.ROOT));
                }
                continue;
            }
            if (piece.length() == 1) {
                tokens.add(piece);
            }
            for (int i = 0; i + 1 < piece.length(); i++) {
                String gram = piece.substring(i, i + 2);
                if (!NOISE.contains(gram)) {
                    tokens.add(gram);
                }
            }
        }
        return tokens;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
package edu.campus.lostfound.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 访问 Python 侧 ai-service 的文本相似度接口。
 *
 * <p>这个类刻意把「AI 不可用」当成正常情况处理：调用方拿到空 Map 就退回本地打分，
 * 因此单独启动后端（不启 ai-service、在地铁上断网）也能跑完整流程。
 *
 * <p>接口形状对齐 {@code POST /similar}：
 * <pre>
 * {"text": "...", "candidates": [{"id": "1", "text": "..."}], "top_k": 5}
 * → {"items": [{"id": "1", "score": 0.4, "shared": ["黑色"]}]}
 * </pre>
 */
@Service
@Profile("local")
public class AiTextClient {

    private static final Logger log = LoggerFactory.getLogger(AiTextClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String baseUrl;
    private final Duration timeout;

    public AiTextClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.enabled:true}") boolean enabled,
            @Value("${app.ai.base-url:http://127.0.0.1:8000}") String baseUrl,
            @Value("${app.ai.timeout-ms:800}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofMillis(Math.max(timeoutMs, 100));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * @return 候选 id → 相似度（0~1）；AI 服务不可用、超时或返回异常时为空 Map。
     */
    public Map<String, Double> rank(String text, Map<String, String> candidates, int topK) {
        if (!enabled || candidates.isEmpty() || text == null || text.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("text", truncate(text));
            // ai-service 的 top_k 上限是 200；后端候选池最多 80 条，取全量分数再在本地排序。
            payload.put("top_k", Math.min(Math.max(topK, 1), 200));
            payload.put("candidates", candidates.entrySet().stream()
                    .map(entry -> Map.of("id", entry.getKey(), "text", truncate(entry.getValue())))
                    .toList());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/similar"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.debug("ai-service /similar 返回 {}，跳过语义打分", response.statusCode());
                return Map.of();
            }

            JsonNode items = objectMapper.readTree(response.body()).path("items");
            Map<String, Double> scores = new LinkedHashMap<>();
            for (JsonNode item : items) {
                scores.put(item.path("id").asText(), item.path("score").asDouble());
            }
            return scores;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (Exception e) {
            // 连接被拒、超时、DNS 失败、响应体不是 JSON 都在这里兜住：匹配功能降级而不是报错。
            log.debug("ai-service 不可用（{}），候选匹配退回本地打分", e.toString());
            return Map.of();
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        // ai-service 对单条文本限制 2000 字符，超长直接截断而不是让整批请求 422。
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
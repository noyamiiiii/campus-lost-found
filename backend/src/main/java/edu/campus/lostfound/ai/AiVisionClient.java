package edu.campus.lostfound.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 调用 ai-service 的图片理解接口（{@code POST /analyze}，multipart）。
 *
 * <p>和 {@link AiTextClient} 一样把「AI 不可用」当正常情况：超时、连不上、返回非 2xx
 * 都返回 {@code null}，调用方据此把图片标成识别失败，但上传本身照常成功。
 */
@Service
@Profile("local")
public class AiVisionClient {

    private static final Logger log = LoggerFactory.getLogger(AiVisionClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String baseUrl;
    private final Duration timeout;

    public AiVisionClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.enabled:true}") boolean enabled,
            @Value("${app.ai.base-url:http://127.0.0.1:8000}") String baseUrl,
            @Value("${app.ai.vision-timeout-ms:4000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofMillis(Math.max(timeoutMs, 500));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(this.timeout.toMillis(), 1500)))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 分析一张图。
     *
     * @param content  图片字节
     * @param mime     MIME 类型，用于 multipart 的 Content-Type
     * @param kind     LOST / FOUND，参与生成标题与描述，可为 null
     * @param location 地点名称，参与生成标题与描述，可为 null
     * @return 识别结果；AI 不可用或失败时为 {@code null}
     */
    public AiAnalysis analyze(byte[] content, String mime, String kind, String location) {
        if (!enabled || content == null || content.length == 0) {
            return null;
        }
        String boundary = "----clf" + UUID.randomUUID().toString().replace("-", "");
        try {
            byte[] body = multipart(boundary, content, mime == null ? "image/jpeg" : mime, kind, location);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/analyze"))
                    .timeout(timeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.debug("ai-service /analyze 返回 {}，图片识别跳过", response.statusCode());
                return null;
            }
            return objectMapper.readValue(response.body(), AiAnalysis.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            // 没启动 ai-service、模型没下载完、超时，都在这里兜住：图片照常保存。
            log.debug("ai-service 图片识别不可用（{}），跳过识别", e.toString());
            return null;
        }
    }

    private byte[] multipart(String boundary, byte[] content, String mime, String kind, String location)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length + 512);
        if (kind != null && !kind.isBlank()) {
            writeField(out, boundary, "kind", kind);
        }
        if (location != null && !location.isBlank()) {
            writeField(out, boundary, "location", location);
        }
        writeField(out, boundary, "with_embedding", "true");

        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"upload"
                + extensionOf(mime) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String extensionOf(String mime) {
        if (mime == null) {
            return ".jpg";
        }
        return switch (mime) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
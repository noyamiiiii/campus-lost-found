package edu.campus.lostfound.storage;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * S3 兼容实现（MinIO / 阿里云 OSS / AWS S3 通用）。
 * 只做单次 PUT，文件上限受 app.storage.max-file-bytes 约束，不需要分片上传。
 */
public class S3Storage implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3Storage.class);
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SERVICE = "s3";
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final StorageProperties.S3 config;
    private final URI endpoint;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public S3Storage(StorageProperties.S3 config) {
        this.config = config;
        this.endpoint = URI.create(trimTrailingSlash(config.getEndpoint()));
        log.info("S3 存储已启用：{} / bucket={}", endpoint, config.getBucket());
    }

    @Override
    public String put(String key, byte[] content, String contentType) {
        String path = "/" + config.getBucket() + "/" + key;
        String payloadHash = sha256Hex(content);
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = AMZ_DATE.format(now);
        String shortDate = SHORT_DATE.format(now);
        String host = hostForSign();

        String canonicalUri = encodePath(path);
        String canonicalHeaders = "content-type:" + contentType + "\n"
                + "host:" + host + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = "PUT\n" + canonicalUri + "\n\n" + canonicalHeaders + "\n"
                + signedHeaders + "\n" + payloadHash;
        String scope = shortDate + "/" + config.getRegion() + "/" + SERVICE + "/aws4_request";
        String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest);

        String signature = hmacHex(signingKey(shortDate), stringToSign);
        String authorization = ALGORITHM + " Credential=" + config.getAccessKey() + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        URI target = URI.create(trimTrailingSlash(config.getEndpoint()) + canonicalUri);
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", contentType)
                // Host 属于 JDK HttpClient 的受限请求头，不能手动设置；它由客户端按 URI 自动生成，
                // 与下面签名里的 host 一致（默认端口时 URI 不带端口，签名里也要去掉端口，见 hostForSign）。
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", amzDate)
                .header("Authorization", authorization)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new UncheckedIOException(new java.io.IOException(
                        "对象存储写入失败：HTTP " + response.statusCode() + " " + response.body()));
            }
        } catch (java.io.IOException ex) {
            throw new UncheckedIOException("对象存储不可用：" + config.getEndpoint(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException("对象存储写入被中断", new java.io.IOException(ex));
        }
        return publicUrl(key);
    }

    @Override
    public void delete(String key) {
        String path = "/" + config.getBucket() + "/" + key;
        String payloadHash = EMPTY_SHA256;
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = AMZ_DATE.format(now);
        String shortDate = SHORT_DATE.format(now);
        String host = hostForSign();
        String canonicalUri = encodePath(path);
        String canonicalHeaders = "host:" + host + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = "DELETE\n" + canonicalUri + "\n\n" + canonicalHeaders + "\n"
                + signedHeaders + "\n" + payloadHash;
        String scope = shortDate + "/" + config.getRegion() + "/" + SERVICE + "/aws4_request";
        String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest);
        String signature = hmacHex(signingKey(shortDate), stringToSign);
        String authorization = ALGORITHM + " Credential=" + config.getAccessKey() + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
        HttpRequest request = HttpRequest.newBuilder(URI.create(trimTrailingSlash(config.getEndpoint()) + canonicalUri))
                .timeout(Duration.ofSeconds(10))
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", amzDate)
                .header("Authorization", authorization)
                .DELETE()
                .build();
        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (java.io.IOException ex) {
            log.warn("对象存储删除失败：{}", key, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String publicUrl(String key) {
        String base = config.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            base = trimTrailingSlash(config.getEndpoint()) + "/" + config.getBucket();
        }
        return trimTrailingSlash(base) + "/" + key;
    }

    /**
     * 签名用的 host 必须和实际发出的请求头一致：JDK HttpClient 会按 URI 生成 Host，
     * 默认端口（http 80 / https 443）不带端口，因此这里也要去掉，否则签名校验不通过（403）。
     */
    private String hostForSign() {
        String host = endpoint.getHost();
        int port = endpoint.getPort();
        String scheme = endpoint.getScheme() == null ? "" : endpoint.getScheme().toLowerCase();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return port > 0 && !defaultPort ? host + ":" + port : host;
    }

    private byte[] signingKey(String shortDate) {
        byte[] dateKey = hmac(("AWS4" + config.getSecretKey()).getBytes(StandardCharsets.UTF_8), shortDate);
        byte[] regionKey = hmac(dateKey, config.getRegion());
        byte[] serviceKey = hmac(regionKey, SERVICE);
        return hmac(serviceKey, "aws4_request");
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("S3 签名失败", ex);
        }
    }

    private static String hmacHex(byte[] key, String data) {
        return HexFormat.of().formatHex(hmac(key, data));
    }

    private static String sha256Hex(String data) {
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("缺少 SHA-256 算法", ex);
        }
    }

    /** 保留 / 分隔符，其余字符按 RFC 3986 编码。 */
    private static String encodePath(String path) {
        StringBuilder builder = new StringBuilder();
        for (char ch : path.toCharArray()) {
            if (ch == '/' || ('A' <= ch && ch <= 'Z') || ('a' <= ch && ch <= 'z')
                    || ('0' <= ch && ch <= '9') || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                builder.append(ch);
            } else {
                for (byte b : String.valueOf(ch).getBytes(StandardCharsets.UTF_8)) {
                    builder.append('%').append(String.format("%02X", b));
                }
            }
        }
        return builder.toString();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
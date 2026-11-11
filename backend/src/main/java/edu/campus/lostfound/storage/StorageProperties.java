package edu.campus.lostfound.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置。本地开发默认写磁盘（provider=local），部署时可用 s3（MinIO 兼容）切换。
 * 文件不直接暴露内部路径，对外统一给 publicBaseUrl + 存储相对路径。
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** local 写本地磁盘；s3 走 S3 兼容接口（MinIO / 阿里云 OSS 均可）。 */
    private String provider = "local";

    /** 本地存储根目录，相对路径按后端工作目录解析。 */
    private String localRoot = "./data/files";

    /** 本地模式下文件的访问前缀，默认由后端静态资源路由提供。 */
    private String localPublicPath = "/files";

    /** 单文件大小上限（字节），默认 5MB。 */
    private long maxFileBytes = 5L * 1024 * 1024;

    /** 每个帖子最多允许的图片数量。 */
    private int maxImagesPerPost = 6;

    private S3 s3 = new S3();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public String getLocalPublicPath() {
        return localPublicPath;
    }

    public void setLocalPublicPath(String localPublicPath) {
        this.localPublicPath = localPublicPath;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public int getMaxImagesPerPost() {
        return maxImagesPerPost;
    }

    public void setMaxImagesPerPost(int maxImagesPerPost) {
        this.maxImagesPerPost = maxImagesPerPost;
    }

    public S3 getS3() {
        return s3;
    }

    public void setS3(S3 s3) {
        this.s3 = s3;
    }

    public static class S3 {

        /** MinIO 地址，例如 http://127.0.0.1:9000。 */
        private String endpoint = "http://127.0.0.1:9000";
        private String region = "us-east-1";
        private String accessKey = "clfadmin";
        private String secretKey = "clf_dev_minio";
        private String bucket = "lostfound";
        /** 对外访问前缀，留空则用 endpoint/bucket 拼。 */
        private String publicBaseUrl = "";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }
    }
}
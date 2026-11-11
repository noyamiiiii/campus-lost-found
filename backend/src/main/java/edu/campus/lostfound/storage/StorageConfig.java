package edu.campus.lostfound.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public StorageService storageService(StorageProperties properties) {
        String provider = properties.getProvider() == null ? "local" : properties.getProvider().trim().toLowerCase();
        if ("s3".equals(provider) || "minio".equals(provider)) {
            return new S3Storage(properties.getS3());
        }
        return new LocalDiskStorage(properties.getLocalRoot(), properties.getLocalPublicPath());
    }
}
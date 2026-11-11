package edu.campus.lostfound.storage;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 本地存储模式下，把 app.storage.local-public-path 映射到磁盘目录，
 * 前端直接用 <img src="/files/xxx.png"> 即可访问，不需要鉴权。
 */
@Configuration
public class StorageWebConfig implements WebMvcConfigurer {

    private final StorageService storageService;
    private final StorageProperties properties;

    public StorageWebConfig(StorageService storageService, StorageProperties properties) {
        this.storageService = storageService;
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!(storageService instanceof LocalDiskStorage local)) {
            return;
        }
        String publicPath = properties.getLocalPublicPath() == null || properties.getLocalPublicPath().isBlank()
                ? "/files"
                : properties.getLocalPublicPath().trim();
        if (!publicPath.startsWith("/")) {
            publicPath = "/" + publicPath;
        }
        if (publicPath.endsWith("/")) {
            publicPath = publicPath.substring(0, publicPath.length() - 1);
        }
        String location = local.getRoot().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(publicPath + "/**").addResourceLocations(location);
    }
}
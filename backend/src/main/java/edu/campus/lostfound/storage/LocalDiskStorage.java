package edu.campus.lostfound.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地磁盘实现：开发环境默认使用，无需 Docker 也能跑通图片上传。
 * 文件通过 WebConfig 注册的静态资源路由对外提供。
 */
public class LocalDiskStorage implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskStorage.class);

    private final Path root;
    private final String publicPath;

    public LocalDiskStorage(String localRoot, String publicPath) {
        this.root = Paths.get(localRoot).toAbsolutePath().normalize();
        this.publicPath = normalizePublicPath(publicPath);
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new UncheckedIOException("无法创建本地文件目录：" + root, ex);
        }
        log.info("本地文件存储已启用：{} -> {}", root, this.publicPath);
    }

    public Path getRoot() {
        return root;
    }

    @Override
    public String put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入文件失败：" + key, ex);
        }
        return publicUrl(key);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            log.warn("删除本地文件失败：{}", key, ex);
        }
    }

    @Override
    public String publicUrl(String key) {
        return publicPath + "/" + key;
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的存储键：" + key);
        }
        return target;
    }

    private static String normalizePublicPath(String value) {
        String path = value == null || value.isBlank() ? "/files" : value.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
package edu.campus.lostfound.file;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.campus.lostfound.common.ApiException;
import edu.campus.lostfound.file.FileDtos.FileView;
import edu.campus.lostfound.storage.StorageProperties;
import edu.campus.lostfound.storage.StorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传：校验类型与大小 -> 落对象存储 -> 写 file_object。
 * 文件先以 ORPHAN 之外的 ACTIVE 状态存下，发帖时通过 imageIds 关联到 item_image。
 */
@Service
@Profile("local")
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String OWNER_TYPE_USER = "USER";
    private static final String PURPOSE_ITEM_IMAGE = "ITEM_IMAGE";

    private final FileObjectMapper fileObjectMapper;
    private final StorageService storageService;
    private final StorageProperties properties;

    public FileService(
            FileObjectMapper fileObjectMapper,
            StorageService storageService,
            StorageProperties properties
    ) {
        this.fileObjectMapper = fileObjectMapper;
        this.storageService = storageService;
        this.properties = properties;
    }

    @Transactional
    public FileView upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择要上传的图片");
        }
        if (file.getSize() > properties.getMaxFileBytes()) {
            throw ApiException.badRequest("图片不能超过 " + (properties.getMaxFileBytes() / 1024 / 1024) + "MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("读取上传文件失败，请重试");
        }
        ImageType type = ImageType.detect(content);
        if (type == null) {
            throw ApiException.badRequest("只支持 JPG、PNG、WebP、GIF 格式的图片");
        }

        String storageKey = "item/" + MONTH_DIR.format(LocalDateTime.now()) + "/"
                + UUID.randomUUID().toString().replace("-", "") + type.extension;
        storageService.put(storageKey, content, type.mime);

        int[] size = readSize(content);
        int width = size[0] > 0 ? size[0] : 0;
        int height = size[1] > 0 ? size[1] : 0;

        FileObjectEntity entity = new FileObjectEntity();
        entity.setOwnerType(OWNER_TYPE_USER);
        entity.setOwnerId(userId);
        entity.setPurpose(PURPOSE_ITEM_IMAGE);
        entity.setStorageKey(storageKey);
        entity.setMime(type.mime);
        entity.setSize((long) content.length);
        entity.setSha256(sha256Hex(content));
        entity.setWidth(width > 0 ? width : null);
        entity.setHeight(height > 0 ? height : null);
        entity.setStatus("ACTIVE");
        fileObjectMapper.insert(entity);

        return new FileView(entity.getId(), storageService.publicUrl(storageKey), type.mime,
                content.length, width, height);
    }

    /** 校验图片归属，避免把别人的图挂到自己的帖子上。 */
    @Transactional(readOnly = true)
    public FileObjectEntity requireOwnedImage(Long userId, Long fileId) {
        FileObjectEntity entity = fileObjectMapper.selectOne(Wrappers.<FileObjectEntity>lambdaQuery()
                .eq(FileObjectEntity::getId, fileId)
                .eq(FileObjectEntity::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (entity == null) {
            throw ApiException.badRequest("图片不存在或已失效，请重新上传");
        }
        if (!userId.equals(entity.getOwnerId())) {
            throw ApiException.forbidden("只能使用自己上传的图片");
        }
        return entity;
    }

    public String urlOf(FileObjectEntity entity) {
        return storageService.publicUrl(entity.getStorageKey());
    }

    public String urlOfKey(String storageKey) {
        return storageService.publicUrl(storageKey);
    }

    private int[] readSize(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image != null) {
                return new int[]{image.getWidth(), image.getHeight()};
            }
        } catch (IOException ex) {
            log.debug("无法解析图片尺寸", ex);
        }
        return new int[]{0, 0};
    }

    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("缺少 SHA-256 算法", ex);
        }
    }

    /** 按魔数判断格式，不信任客户端提交的 Content-Type。 */
    private enum ImageType {

        JPEG(".jpg", "image/jpeg"),
        PNG(".png", "image/png"),
        GIF(".gif", "image/gif"),
        WEBP(".webp", "image/webp");

        private final String extension;
        private final String mime;

        ImageType(String extension, String mime) {
            this.extension = extension;
            this.mime = mime;
        }

        static ImageType detect(byte[] content) {
            if (content == null || content.length < 12) {
                return null;
            }
            if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
                return JPEG;
            }
            if ((content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
                return PNG;
            }
            if (content[0] == 'G' && content[1] == 'I' && content[2] == 'F') {
                return GIF;
            }
            String riff = new String(content, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
            String webp = new String(content, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if ("RIFF".equals(riff) && "WEBP".equals(webp)) {
                return WEBP;
            }
            return null;
        }

        String extension() {
            return extension;
        }
    }
}
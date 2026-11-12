package edu.campus.lostfound.file;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campus.lostfound.ai.AiAnalysis;
import edu.campus.lostfound.ai.AiVisionClient;
import edu.campus.lostfound.common.ApiException;
import edu.campus.lostfound.file.FileDtos.AiView;
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
import java.util.List;
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
    private final AiVisionClient aiVisionClient;
    private final ObjectMapper objectMapper;

    public FileService(
            FileObjectMapper fileObjectMapper,
            StorageService storageService,
            StorageProperties properties,
            AiVisionClient aiVisionClient,
            ObjectMapper objectMapper
    ) {
        this.fileObjectMapper = fileObjectMapper;
        this.storageService = storageService;
        this.properties = properties;
        this.aiVisionClient = aiVisionClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FileView upload(Long userId, MultipartFile file) {
        return upload(userId, file, null, null);
    }

    /**
     * @param kind     LOST / FOUND，可空；影响 AI 生成的标题措辞
     * @param location 地点名称，可空；参与 AI 生成的标题
     */
    @Transactional
    public FileView upload(Long userId, MultipartFile file, String kind, String location) {
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
        entity.setAiStatus("PENDING");
        fileObjectMapper.insert(entity);

        applyAnalysis(entity, content, type.mime, kind, location);
        return toView(entity);
    }

    /**
     * 图片理解：AI 不可用时不影响上传结果，只把状态标成 SKIPPED/FAILED。
     * 识别结果同时写库（供发帖复用）与响应体（供前端「智能填写」）。
     */
    private void applyAnalysis(FileObjectEntity entity, byte[] content, String mime, String kind, String location) {
        if (!aiVisionClient.isEnabled()) {
            entity.setAiStatus("SKIPPED");
            return;
        }
        AiAnalysis analysis = aiVisionClient.analyze(content, mime, kind, location);
        if (analysis == null) {
            entity.setAiStatus("FAILED");
            fileObjectMapper.updateById(entity);
            return;
        }
        entity.setAiStatus("DONE");
        entity.setAiModel(analysis.model());
        entity.setAiConfidence(java.math.BigDecimal.valueOf(round4(analysis.category() == null
                ? 0d : analysis.category().confidence())));
        if (analysis.category() != null) {
            entity.setAiCategoryId(analysis.category().id());
        }
        entity.setAiLabels(writeJson(analysis.labels()));
        entity.setAiColors(writeJson(analysis.colors()));
        entity.setAiSensitive(writeJson(analysis.sensitive()));
        entity.setAiDescription(truncate(analysis.description(), 1000));
        entity.setAiTitle(truncate(analysis.title(), 120));
        entity.setAiAnalyzedAt(LocalDateTime.now());
        if (analysis.embedding() != null && !analysis.embedding().isEmpty()) {
            entity.setEmbedding(toFloat32(analysis.embedding()));
            entity.setEmbeddingModel(analysis.model());
            entity.setEmbeddingVersion("v1");
            entity.setEmbeddingCreatedAt(LocalDateTime.now());
        }
        fileObjectMapper.updateById(entity);
    }

    /** 单张图片的展示视图，含 AI 结果（用于上传响应与帖子详情）。 */
    public FileView toView(FileObjectEntity entity) {
        return new FileView(
                entity.getId(),
                storageService.publicUrl(entity.getStorageKey()),
                entity.getMime(),
                entity.getSize() == null ? 0L : entity.getSize(),
                entity.getWidth(),
                entity.getHeight(),
                aiViewOf(entity));
    }

    private AiView aiViewOf(FileObjectEntity entity) {
        String status = entity.getAiStatus() == null ? "PENDING" : entity.getAiStatus();
        if (!"DONE".equals(status)) {
            return "SKIPPED".equals(status) ? AiView.skipped() : AiView.failed();
        }
        AiAnalysis.Category category = entity.getAiCategoryId() == null
                ? null
                : new AiAnalysis.Category(entity.getAiCategoryId(), categoryLabel(entity.getAiCategoryId()),
                entity.getAiConfidence() == null ? 0d : entity.getAiConfidence().doubleValue());
        return new AiView(
                status,
                entity.getAiCategoryId(),
                category == null ? null : category.label(),
                category == null ? 0d : category.confidence(),
                readJson(entity.getAiColors(), new TypeReference<List<AiAnalysis.Color>>() {
                }),
                readJson(entity.getAiLabels(), new TypeReference<List<AiAnalysis.Label>>() {
                }),
                readJson(entity.getAiSensitive(), new TypeReference<AiAnalysis.Sensitive>() {
                }),
                nullToEmpty(entity.getAiDescription()),
                nullToEmpty(entity.getAiTitle()),
                nullToEmpty(entity.getAiModel()),
                entity.getAiConfidence() == null);
    }

    private String categoryLabel(Integer categoryId) {
        return switch (categoryId == null ? 0 : categoryId) {
            case 1 -> "校园卡";
            case 2 -> "身份证件";
            case 3 -> "钥匙";
            case 4 -> "手机";
            case 5 -> "耳机";
            case 6 -> "平板";
            case 7 -> "笔记本电脑";
            case 8 -> "充电宝";
            case 9 -> "书籍";
            case 10 -> "水杯";
            case 11 -> "雨伞";
            case 12 -> "背包";
            case 13 -> "钱包";
            case 14 -> "眼镜";
            case 15 -> "手表";
            case 16 -> "衣物";
            default -> "其他";
        };
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.debug("序列化 AI 结果失败", ex);
            return null;
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            log.debug("反序列化 AI 结果失败", ex);
            return null;
        }
    }

    private static byte[] toFloat32(List<Float> values) {
        byte[] bytes = new byte[values.size() * 4];
        for (int i = 0; i < values.size(); i++) {
            int bits = Float.floatToIntBits(values.get(i));
            bytes[i * 4] = (byte) (bits >>> 24);
            bytes[i * 4 + 1] = (byte) (bits >>> 16);
            bytes[i * 4 + 2] = (byte) (bits >>> 8);
            bytes[i * 4 + 3] = (byte) bits;
        }
        return bytes;
    }

    private static double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
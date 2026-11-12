package edu.campus.lostfound.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * ai-service {@code POST /analyze} 的响应体。
 *
 * <p>字段与 Python 侧的 pydantic 模型一一对应，用 snake_case 注解直连，避免再写一层
 * 转换对象；缺失字段按 null/空处理，前端展示时按需兜底。
 */
public record AiAnalysis(
        int width,
        int height,
        Category category,
        List<Label> labels,
        List<Color> colors,
        Sensitive sensitive,
        String description,
        String title,
        List<Float> embedding,
        String model,
        boolean degraded
) {

    public record Category(int id, String label, double confidence) {
    }

    public record Label(String name, double score) {
    }

    public record Color(String hex, String name, double ratio) {
    }

    public record Sensitive(
            @JsonProperty("has_face") boolean hasFace,
            @JsonProperty("has_id_card") boolean hasIdCard,
            List<Region> boxes
    ) {
    }

    public record Region(String type, int x, int y, int w, int h, double score) {
    }
}
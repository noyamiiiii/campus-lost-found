package edu.campus.lostfound.file;

import edu.campus.lostfound.ai.AiAnalysis;
import java.util.List;

public final class FileDtos {

    private FileDtos() {
    }

    /**
     * 上传结果。id 用于发帖时引用（imageIds），url 直接给前端 &lt;img src&gt;，
     * ai 是图片识别结果（可能为空：AI 服务没启动、模型没下载完都会走到这里）。
     */
    public record FileView(
            Long id,
            String url,
            String mime,
            long size,
            Integer width,
            Integer height,
            AiView ai
    ) {
    }

    /**
     * 图片识别结果。status 取值 PENDING（识别中）、DONE（成功）、FAILED（失败）、
     * SKIPPED（开关关掉了），前端按状态决定是否展示「智能填写」。
     */
    public record AiView(
            String status,
            Integer categoryId,
            String categoryLabel,
            double confidence,
            List<AiAnalysis.Color> colors,
            List<AiAnalysis.Label> labels,
            AiAnalysis.Sensitive sensitive,
            String description,
            String title,
            String model,
            boolean degraded
    ) {

        public static AiView skipped() {
            return new AiView("SKIPPED", null, null, 0d, List.of(), List.of(), null, "", "", "", true);
        }

        public static AiView failed() {
            return new AiView("FAILED", null, null, 0d, List.of(), List.of(), null, "", "", "", true);
        }
    }
}
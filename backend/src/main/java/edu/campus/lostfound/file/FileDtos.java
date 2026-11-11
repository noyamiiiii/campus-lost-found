package edu.campus.lostfound.file;

public final class FileDtos {

    private FileDtos() {
    }

    /**
     * 上传结果。id 用于发帖时引用（imageIds），url 直接给前端 <img src>。
     */
    public record FileView(
            Long id,
            String url,
            String mime,
            long size,
            Integer width,
            Integer height
    ) {
    }
}
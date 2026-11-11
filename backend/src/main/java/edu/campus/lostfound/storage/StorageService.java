package edu.campus.lostfound.storage;

/**
 * 对象存储抽象。业务侧只关心「存一个 key、拿回可访问 URL」，不关心落地是磁盘还是 MinIO。
 */
public interface StorageService {

    /**
     * 写入文件，返回对外可访问的相对或绝对 URL。
     *
     * @param key         存储键，形如 item/202611/ab12cd.png，不能以 / 开头
     * @param content     文件内容
     * @param contentType MIME 类型
     */
    String put(String key, byte[] content, String contentType);

    /** 删除文件，不存在时静默忽略。 */
    void delete(String key);

    /** 由存储键还原访问 URL。 */
    String publicUrl(String key);
}
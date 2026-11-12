package edu.campus.lostfound.post;

/**
 * 一条图片向量的查询结果：哪条帖子、向量本体。
 *
 * <p>向量在 item_image.embedding 里是 L2 归一化后的 float32，直接算点积就是余弦相似度。
 */
public class EmbeddingRow {

    private Long postId;
    private byte[] embedding;

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }
}
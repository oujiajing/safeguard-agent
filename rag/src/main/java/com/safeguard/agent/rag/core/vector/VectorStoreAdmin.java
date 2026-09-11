package com.safeguard.agent.rag.core.vector;

/**
 * 向量空间元数据/索引管理（与检索解耦）
 * 用于确保空间存在：不存在就按规格创建；存在则校验兼容性
 */
public interface VectorStoreAdmin {

    /**
     * 幂等：确保向量空间存在（不存在则创建）
     *
     * @param spec 向量空间规格（跨引擎统一定义）
     */
    void ensureVectorSpace(VectorSpaceSpec spec);

    /**
     * 只判断存在性（不创建）
     */
    boolean vectorSpaceExists(VectorSpaceId spaceId);

    /**
     * 幂等：销毁向量空间（与 {@link #ensureVectorSpace} 对应）
     * <p>
     * - Milvus：删除该知识库对应的 collection（不存在则跳过）
     * - PG：删除共享表中属于该 collection 的残留向量行（不动共享 HNSW 索引）
     *
     * @param collectionName 知识库 collection 名称
     */
    void dropVectorSpace(String collectionName);
}

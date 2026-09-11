package com.safeguard.agent.core.ingest;

/**
 * 向量落点身份：块写到哪个逻辑分区、用哪个模型、必须是多少维，由知识库配置（L2）与部署配置（L1）合成
 * <p>
 * {@link #partition} 是逻辑分区键，与 {@code rag.core.vector.VectorSpaceId} 表示的物理空间（PG 下是共享表与共享索引，
 * Milvus 下是 collection）不是一回事，两者都别叫 collectionName；模型与维度随身携带，缺一个都不允许落到系统默认值
 *
 * @param partition      逻辑分区键，取自知识库的 collection_name
 * @param embeddingModel 嵌入模型 ID，取自知识库配置
 * @param dimension      向量维度，取自部署级配置，全局硬约束
 */
public record VectorTarget(String partition, String embeddingModel, int dimension) {

    public VectorTarget {
        if (partition == null || partition.isBlank()) {
            throw new IllegalArgumentException("partition 不能为空");
        }
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("embeddingModel 不能为空，partition=" + partition
                    + "——嵌入模型是知识库级约束性配置，不允许回落到系统默认");
        }
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension 必须 > 0，实际 " + dimension);
        }
    }
}

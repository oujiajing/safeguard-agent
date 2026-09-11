package com.safeguard.agent.core.ingest;

/**
 * 摄取内核：固定五步骨架，调用方不可跳过、不可换序、不可替换
 * <pre>
 *   ① identity   字节 + 文件名 ──▶ MIME          全链路唯一一次，无入参可传错
 *   ② parse      (MIME × 档位) ──▶ List&lt;Block&gt;
 *   ③ chunk      Block 类型 → chunker + 预算 ──▶ List&lt;Chunk&gt;
 *   ④ embed      向量化，此处校验维度
 *   ⑤ index      ChunkSink 扇出，事务边界在此
 * </pre>
 * 取数是内核之前的事；任务状态流转与摄取日志归外层
 */
public interface IngestionKernel {

    /**
     * 执行一次完整摄取：解析 → 分块 → 向量化 → 落库
     *
     * @param doc   文档身份，决定资产归属与落库归属
     * @param bytes 文件字节
     * @param spec  文档级配置：解析档位 + 分块预算
     * @param target 向量落点：逻辑分区 + 嵌入模型 + 维度
     * @return 摄取结果
     */
    IngestionOutcome run(DocumentRef doc,
                         byte[] bytes,
                         IngestionSpec spec,
                         VectorTarget target);
}

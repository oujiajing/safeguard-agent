package com.safeguard.agent.rag.core.retrieval.channel;

/**
 * 检索通道类型枚举
 */
public enum SearchChannelType {

    /**
     * 向量检索
     * 一条向量模态通道，按 KB 意图置信度在通道内决定作用域：
     * 有足够置信的 KB 意图时收窄到命中库（意图定向），否则退化为全库检索（全局）
     */
    VECTOR,

    /**
     * 关键词检索
     * 基于全文检索引擎（如 Elasticsearch）的关键词分词检索，后端为实现细节
     */
    KEYWORD,

    /**
     * 联网检索
     * 基于外部 Web 搜索 API（如 You.com Search）的实时网络召回，与本地知识库通道互补
     */
    WEB_SEARCH,

    /**
     * 混合检索
     * 结合多种检索策略
     */
    HYBRID
}

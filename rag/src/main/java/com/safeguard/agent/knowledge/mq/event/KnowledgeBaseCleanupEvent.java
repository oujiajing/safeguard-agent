package com.safeguard.agent.knowledge.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库删除清理任务事件
 * 负责异步回收知识库独占的底层物理资源（Milvus collection / bucket / 残留向量）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseCleanupEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识库 ID
     */
    private String kbId;

    /**
     * 知识库 collection 名称（同时也是 bucket 名）
     */
    private String collectionName;

    /**
     * 操作人
     */
    private String operator;
}

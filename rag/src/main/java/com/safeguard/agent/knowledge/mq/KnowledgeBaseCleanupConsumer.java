package com.safeguard.agent.knowledge.mq;

import com.safeguard.agent.framework.exception.ServiceException;
import com.safeguard.agent.framework.mq.MessageWrapper;
import com.safeguard.agent.knowledge.mq.event.KnowledgeBaseCleanupEvent;
import com.safeguard.agent.rag.core.keyword.KeywordIndexService;
import com.safeguard.agent.rag.core.vector.VectorStoreAdmin;
import com.safeguard.agent.rag.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 知识库删除清理 MQ 消费者。
 * 负责异步回收知识库独占的向量数据、对象存储目录和可选 ES 关键词索引。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "knowledge-base-cleanup_topic${unique-name:}",
        consumerGroup = "knowledge-base-cleanup_cg${unique-name:}"
)
public class KnowledgeBaseCleanupConsumer implements RocketMQListener<MessageWrapper<KnowledgeBaseCleanupEvent>> {

    private final VectorStoreAdmin vectorStoreAdmin;
    private final FileStorageService fileStorageService;
    /**
     * rag.keyword.type=none 时没有实现，getIfAvailable() 返回 null，跳过 ES 清理。
     */
    private final ObjectProvider<KeywordIndexService> keywordIndexServiceProvider;

    @Override
    public void onMessage(MessageWrapper<KnowledgeBaseCleanupEvent> message) {
        KnowledgeBaseCleanupEvent event = message.getBody();
        String collectionName = event.getCollectionName();

        log.info("[消费者] 开始清理知识库物理资源，kbId={}, collectionName={}", event.getKbId(), collectionName);

        boolean allSucceeded = true;

        try {
            vectorStoreAdmin.dropVectorSpace(collectionName);
        } catch (Exception e) {
            allSucceeded = false;
            log.error("清理向量空间失败，collectionName={}", collectionName, e);
        }

        try {
            fileStorageService.deleteKnowledgeSpace(collectionName);
        } catch (Exception e) {
            allSucceeded = false;
            log.error("删除知识库存储目录失败，namespace={}", collectionName, e);
        }

        KeywordIndexService keywordIndexService = keywordIndexServiceProvider.getIfAvailable();
        if (keywordIndexService != null) {
            try {
                keywordIndexService.deleteByCollection(collectionName);
            } catch (Exception e) {
                allSucceeded = false;
                log.error("删除 ES 关键词索引失败，collectionName={}", collectionName, e);
            }
        }

        if (!allSucceeded) {
            throw new ServiceException("知识库物理资源清理存在失败项，触发重试");
        }
    }
}

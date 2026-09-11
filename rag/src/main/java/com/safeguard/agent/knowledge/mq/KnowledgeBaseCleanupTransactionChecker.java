package com.safeguard.agent.knowledge.mq;

import cn.hutool.json.JSONUtil;
import com.safeguard.agent.framework.mq.MessageWrapper;
import com.safeguard.agent.framework.mq.producer.DelegatingTransactionListener;
import com.safeguard.agent.framework.mq.producer.TransactionChecker;
import com.safeguard.agent.knowledge.dao.entity.KnowledgeBaseDO;
import com.safeguard.agent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.safeguard.agent.knowledge.mq.event.KnowledgeBaseCleanupEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 知识库删除清理事务消息回查器
 * <p>
 * 按 topic 注册，Broker 回查时可路由到任意实例，通过查询 DB 中知识库是否已逻辑删除判断本地事务是否已提交
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseCleanupTransactionChecker implements TransactionChecker {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DelegatingTransactionListener transactionListener;

    @Value("knowledge-base-cleanup_topic${unique-name:}")
    private String cleanupTopic;

    @PostConstruct
    public void init() {
        transactionListener.registerChecker(cleanupTopic, this);
    }

    @Override
    public boolean check(MessageWrapper<?> message) {
        log.info("[事务回查] 知识库删除清理，消息体：{}", JSONUtil.toJsonStr(message));

        KnowledgeBaseCleanupEvent event = (KnowledgeBaseCleanupEvent) message.getBody();
        // 逻辑删除后 selectById 不可见；查不到即视为本地事务（软删知识库）已提交
        KnowledgeBaseDO kbDO = knowledgeBaseMapper.selectById(event.getKbId());
        return kbDO == null;
    }
}

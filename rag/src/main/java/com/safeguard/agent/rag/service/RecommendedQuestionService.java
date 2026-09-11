package com.safeguard.agent.rag.service;

import com.safeguard.agent.rag.dto.RecommendedQuestionsPayload;

/**
 * 推荐追问问题服务
 * <p>
 * 推荐问题缓存读取与生成入口
 */
public interface RecommendedQuestionService {

    /**
     * 幂等生成指定 assistant 消息的推荐追问问题
     *
     * @param messageId 消息ID（须为 assistant 消息）
     * @param userId    用户ID（校验归属）
     * @return 推荐追问结果
     */
    RecommendedQuestionsPayload generate(String messageId, String userId);
}

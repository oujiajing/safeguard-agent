package com.safeguard.agent.rag.core.retrieval.channel;

import com.safeguard.agent.rag.core.retrieval.RetrievalBudget;
import com.safeguard.agent.rag.dto.SubQuestionIntent;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索上下文
 * <p>
 * 携带检索所需的所有信息，在多个通道之间传递
 */
@Data
@Builder
public class SearchContext {

    /**
     * 原始问题
     */
    private String originalQuestion;

    /**
     * 重写后的问题
     */
    private String rewrittenQuestion;

    /**
     * 子问题列表
     */
    private List<String> subQuestions;

    /**
     * 意图识别结果
     */
    private List<SubQuestionIntent> intents;

    /**
     * 检索预算：召回扇出 / Rerank 候选池上限 / 最终条数，三段各自独立
     * 各阶段只读属于自己的那一段，避免用一个 topK 承载多重语义
     */
    private RetrievalBudget budget;

    /**
     * 检索作用域：定向命中库还是全库，请求入口算一次，各通道共读一份
     */
    private RetrievalScope retrievalScope;

    /**
     * 扩展元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 获取主问题（优先使用重写后的问题）
     */
    public String getMainQuestion() {
        return rewrittenQuestion != null ? rewrittenQuestion : originalQuestion;
    }
}

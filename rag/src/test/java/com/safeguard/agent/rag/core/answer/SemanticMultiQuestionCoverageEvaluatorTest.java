package com.safeguard.agent.rag.core.answer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.infra.chat.LLMService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticMultiQuestionCoverageEvaluatorTest {
    @Test
    void acceptsSemanticParaphraseWhenJudgeConfirmsCoverage() {
        LLMService llm = mock(LLMService.class);
        when(llm.chat(any())).thenReturn("{\"complete\":true}");
        assertTrue(new SemanticMultiQuestionCoverageEvaluator(llm, new ObjectMapper()).isComplete(
                "进入现场应戴好头部防护用品；楼层边缘须设置防护栏。", List.of("安全帽佩戴要求", "临边防护要求")));
    }

    @Test
    void rejectsAnswerWhenJudgeFindsAnOmission() {
        LLMService llm = mock(LLMService.class);
        when(llm.chat(any())).thenReturn("{\"complete\":false}");
        assertFalse(new SemanticMultiQuestionCoverageEvaluator(llm, new ObjectMapper()).isComplete(
                "进入现场应戴好头部防护用品。", List.of("安全帽佩戴要求", "临边防护要求")));
    }
}

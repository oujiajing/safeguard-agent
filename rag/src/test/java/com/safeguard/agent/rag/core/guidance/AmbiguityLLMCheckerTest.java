package com.safeguard.agent.rag.core.guidance;

import com.safeguard.agent.framework.convention.ChatRequest;
import com.safeguard.agent.infra.chat.LLMService;
import com.safeguard.agent.infra.enums.Tier;
import com.safeguard.agent.rag.core.intent.IntentNode;
import com.safeguard.agent.rag.core.intent.NodeScore;
import com.safeguard.agent.rag.core.prompt.PromptTemplateLoader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmbiguityLLMCheckerTest {

    private final LLMService llmService = mock(LLMService.class);
    private final AmbiguityLLMChecker checker = new AmbiguityLLMChecker(
            llmService, new PromptTemplateLoader(new DefaultResourceLoader()));

    @Test
    void returnsTrueWhenLlmReportsAmbiguous() {
        when(llmService.chat(any(), any())).thenReturn("{\"ambiguous\": true, \"reason\": \"两个系统都可能\"}");

        assertTrue(checker.checkAmbiguity("数据安全怎么做", ranked()));
    }

    @Test
    void returnsFalseWhenLlmReportsUnambiguous() {
        when(llmService.chat(any(), any())).thenReturn("```json\n{\"ambiguous\": false, \"reason\": \"要求对比\"}\n```");

        assertFalse(checker.checkAmbiguity("两个系统有什么区别", ranked()));
    }

    @Test
    void fallsBackToFalseOnNonJsonResponse() {
        when(llmService.chat(any(), any())).thenReturn("我认为存在歧义");

        assertFalse(checker.checkAmbiguity("数据安全怎么做", ranked()));
    }

    @Test
    void fallsBackToFalseWhenAmbiguousFieldMissing() {
        when(llmService.chat(any(), any())).thenReturn("{\"reason\": \"无法判断\"}");

        assertFalse(checker.checkAmbiguity("数据安全怎么做", ranked()));
    }

    @Test
    void fallsBackToFalseWhenLlmThrows() {
        when(llmService.chat(any(), any())).thenThrow(new IllegalStateException("模型超时"));

        assertFalse(checker.checkAmbiguity("数据安全怎么做", ranked()));
    }

    @Test
    void sendsIdFullPathAndDescriptionToLlm() {
        when(llmService.chat(any(), any())).thenReturn("{\"ambiguous\": false}");

        checker.checkAmbiguity("数据安全怎么做", ranked());

        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).chat(request.capture(), any(Tier.class));
        String prompt = request.getValue().getMessages().get(0).getContent();
        assertTrue(prompt.contains("数据安全怎么做"));
        assertTrue(prompt.contains("oa-sec"));
        assertTrue(prompt.contains("业务系统 > OA系统 > 数据安全"));
        assertTrue(prompt.contains("OA 系统的数据安全规范"));
        assertTrue(prompt.contains("ins-sec"));
        assertTrue(prompt.contains("业务系统 > 保险系统 > 数据安全"));
        assertTrue(prompt.contains("0.90"));
    }

    private static List<NodeScore> ranked() {
        IntentNode oaSecurity = IntentNode.builder()
                .id("oa-sec")
                .name("数据安全")
                .description("OA 系统的数据安全规范")
                .fullPath("业务系统 > OA系统 > 数据安全")
                .build();
        IntentNode insuranceSecurity = IntentNode.builder()
                .id("ins-sec")
                .name("数据安全")
                .fullPath("业务系统 > 保险系统 > 数据安全")
                .build();
        return List.of(new NodeScore(oaSecurity, 0.9D), new NodeScore(insuranceSecurity, 0.86D));
    }
}

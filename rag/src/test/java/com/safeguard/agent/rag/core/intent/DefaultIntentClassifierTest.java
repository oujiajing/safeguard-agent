package com.safeguard.agent.rag.core.intent;

import com.safeguard.agent.infra.chat.LLMService;
import com.safeguard.agent.rag.core.prompt.PromptTemplateLoader;
import com.safeguard.agent.rag.dao.entity.IntentNodeDO;
import com.safeguard.agent.rag.dao.mapper.IntentNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultIntentClassifierTest {

    @Mock
    private LLMService llmService;

    @Mock
    private IntentNodeMapper intentNodeMapper;

    @Mock
    private PromptTemplateLoader promptTemplateLoader;

    @Mock
    private IntentTreeCacheManager intentTreeCacheManager;

    private DefaultIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new DefaultIntentClassifier(
                llmService,
                intentNodeMapper,
                promptTemplateLoader,
                intentTreeCacheManager
        );
    }

    @Test
    void skipsLlmWhenIntentTreeIsEmpty() {
        when(intentTreeCacheManager.getIntentTreeFromCache()).thenReturn(List.of());
        when(intentNodeMapper.selectList(any())).thenReturn(List.of());

        List<NodeScore> scores = classifier.classifyTargets("How do I apply for leave?");

        assertTrue(scores.isEmpty());
        verifyNoInteractions(llmService, promptTemplateLoader);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unwrapsJsonExamplesBeforeRenderingPrompt() {
        IntentNodeDO node = IntentNodeDO.builder()
                .intentCode("sys-feedback")
                .name("评价反馈")
                .level(1)
                .kind(1)
                .description("用户对上一轮回答做出评价")
                .examples("[\"回答得不错\",\"你答错了\"]")
                .build();
        when(intentTreeCacheManager.getIntentTreeFromCache()).thenReturn(null);
        when(intentNodeMapper.selectList(any())).thenReturn(List.of(node));
        when(promptTemplateLoader.render(anyString(), anyMap())).thenReturn("system-prompt");
        when(llmService.chat(any())).thenReturn("[]");

        classifier.classifyTargets("回答得不错");

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(promptTemplateLoader).render(anyString(), captor.capture());
        assertTrue(captor.getValue().get("intent_list").contains("examples=回答得不错 / 你答错了"));
    }
}

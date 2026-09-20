package com.safeguard.agent.rag.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.safeguard.agent.infra.chat.LLMService;
import com.safeguard.agent.rag.config.RAGConfigProperties;
import com.safeguard.agent.rag.core.prompt.PromptTemplateLoader;
import com.safeguard.agent.rag.core.rewrite.MultiQuestionRewriteService;
import com.safeguard.agent.rag.core.rewrite.QueryTermMappingService;
import com.safeguard.agent.rag.core.rewrite.RewriteResult;
import org.junit.jupiter.api.Test;

class MultiQuestionRewriteServiceUnitTest {

    @Test
    void disabledRewriteStillNormalizesAndSplitsMultipleQuestions() {
        LLMService llm = mock(LLMService.class);
        RAGConfigProperties config = new RAGConfigProperties();
        config.setQueryRewriteEnabled(false);
        QueryTermMappingService mappings = mock(QueryTermMappingService.class);
        PromptTemplateLoader loader = mock(PromptTemplateLoader.class);
        when(mappings.normalize("脚手架问题；临边问题")).thenReturn("脚手架问题；临边问题");

        MultiQuestionRewriteService service = new MultiQuestionRewriteService(llm, config, mappings, loader);
        RewriteResult result = service.rewriteWithSplit("脚手架问题；临边问题", java.util.List.of());

        assertThat(result.rewrittenQuestion()).isEqualTo("脚手架问题；临边问题");
        assertThat(result.subQuestions()).containsExactly("脚手架问题？", "临边问题？");
    }

    @Test
    void deterministicSplitPreservesSeparateRequirementsWhenLlmRewriteIsUnavailable() {
        LLMService llm = mock(LLMService.class);
        RAGConfigProperties config = new RAGConfigProperties();
        config.setQueryRewriteEnabled(false);
        QueryTermMappingService mappings = mock(QueryTermMappingService.class);
        PromptTemplateLoader loader = mock(PromptTemplateLoader.class);
        String question = "雨雪结冰天气进行高处作业，人员防滑和安全带分别有什么要求？";
        when(mappings.normalize(question)).thenReturn(question);

        MultiQuestionRewriteService service = new MultiQuestionRewriteService(llm, config, mappings, loader);
        RewriteResult result = service.rewriteWithSplit(question, java.util.List.of());

        assertThat(result.subQuestions()).containsExactly(
                "雨雪结冰天气进行高处作业，人员防滑有什么要求？",
                "雨雪结冰天气进行高处作业，安全带有什么要求？");
    }

    @Test
    void deterministicSplitHandlesTwoVisibleHazardsWithSeparateAction() {
        LLMService llm = mock(LLMService.class);
        RAGConfigProperties config = new RAGConfigProperties();
        config.setQueryRewriteEnabled(false);
        QueryTermMappingService mappings = mock(QueryTermMappingService.class);
        PromptTemplateLoader loader = mock(PromptTemplateLoader.class);
        String question = "施工现场同时存在500毫米洞口未封堵和基坑作业平台临边无栏杆，应分别采取什么措施？";
        when(mappings.normalize(question)).thenReturn(question);

        MultiQuestionRewriteService service = new MultiQuestionRewriteService(llm, config, mappings, loader);
        RewriteResult result = service.rewriteWithSplit(question, java.util.List.of());

        assertThat(result.subQuestions()).containsExactly(
                "500毫米洞口未封堵应采取什么措施？",
                "基坑作业平台临边无栏杆应采取什么措施？");
    }
}

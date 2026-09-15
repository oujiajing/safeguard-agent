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
}

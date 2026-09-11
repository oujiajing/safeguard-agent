package com.safeguard.agent.agent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentMemoryPipelineTest {

    private static final String USER_ID = "u-1001";

    private AgentMemoryRepository memoryRepository;
    private AgentMemoryProperties memoryProperties;
    private AgentMemoryPipeline pipeline;

    @BeforeEach
    void setUp() {
        memoryRepository = mock(AgentMemoryRepository.class);
        memoryProperties = new AgentMemoryProperties();
        pipeline = new AgentMemoryPipeline(memoryRepository, mock(AgentMemoryJudge.class),
                mock(AgentMemoryConsolidator.class), memoryProperties);
    }

    @Test
    void shouldEnsureControlWhenLongTermEnabled() {
        pipeline.ensureExtractionBaseline(USER_ID);

        verify(memoryRepository).ensureControl(USER_ID);
    }

    @Test
    void shouldSkipBaselineWhenLongTermDisabled() {
        memoryProperties.setLongTermEnabled(false);

        pipeline.ensureExtractionBaseline(USER_ID);

        verifyNoInteractions(memoryRepository);
    }

    /**
     * 预建炸了不许拦对话：长期记忆是增强不是前提，代价只是本轮消息可能漏出下界
     */
    @Test
    void shouldSwallowBaselineFailure() {
        doThrow(new IllegalStateException("库连不上")).when(memoryRepository).ensureControl(USER_ID);

        assertThatCode(() -> pipeline.ensureExtractionBaseline(USER_ID)).doesNotThrowAnyException();
    }
}

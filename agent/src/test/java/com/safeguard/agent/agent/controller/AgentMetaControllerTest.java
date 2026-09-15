package com.safeguard.agent.agent.controller;

import com.safeguard.agent.agent.config.AgentProperties;
import com.safeguard.agent.agent.controller.vo.AgentMetaVO;
import com.safeguard.agent.agent.tool.AgentToolCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMetaControllerTest {

    private AgentToolCatalog toolCatalog;
    private AgentMetaController controller;

    @BeforeEach
    void setUp() {
        toolCatalog = mock(AgentToolCatalog.class);
        AgentProperties properties = new AgentProperties();
        properties.getChat().setModel("qwen-max");
        controller = new AgentMetaController(properties, toolCatalog);
    }

    @Test
    void shouldNotClaimMcpToolsWhenNoneAvailable() {
        when(toolCatalog.mcpToolCount()).thenReturn(0);

        AgentMetaVO meta = controller.meta().getData();

        // 能力清单说有、mcpConfigured 说没有，两个字段各说各话
        assertThat(meta.capabilities()).containsExactly(
                "react", "knowledge-base", "safety-assessment", "image-input", "human-confirmation");
        assertThat(meta.mcpConfigured()).isFalse();
    }

    @Test
    void shouldClaimMcpToolsWhenAvailable() {
        when(toolCatalog.mcpToolCount()).thenReturn(2);

        AgentMetaVO meta = controller.meta().getData();

        assertThat(meta.capabilities()).containsExactly(
                "react", "knowledge-base", "safety-assessment", "image-input", "human-confirmation", "mcp-tools");
        assertThat(meta.mcpConfigured()).isTrue();
    }
}

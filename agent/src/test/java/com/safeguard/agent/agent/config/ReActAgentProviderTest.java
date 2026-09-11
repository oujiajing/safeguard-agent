package com.safeguard.agent.agent.config;

import com.safeguard.agent.agent.confirm.AgentConfirmDenialMiddleware;
import com.safeguard.agent.agent.memory.AgentContextCompactionMiddleware;
import com.safeguard.agent.agent.memory.AgentMemoryPipeline;
import com.safeguard.agent.agent.memory.AgentMemoryProperties;
import com.safeguard.agent.agent.memory.AgentUserMemoryMiddleware;
import com.safeguard.agent.agent.service.AgentConversationService;
import com.safeguard.agent.agent.skill.AgentSkillMaskingMiddleware;
import com.safeguard.agent.agent.state.PgAgentStateStore;
import com.safeguard.agent.agent.tool.AgentToolCatalog;
import com.safeguard.agent.agent.tool.KnowledgeSearchTool;
import com.safeguard.agent.rag.core.intent.IntentNode;
import com.safeguard.agent.rag.core.intent.IntentNodeRegistry;
import com.safeguard.agent.rag.core.mcp.McpToolExecutor;
import com.safeguard.agent.rag.core.mcp.McpToolRegistry;
import com.safeguard.agent.rag.core.prompt.AgentPromptResolver;
import com.safeguard.agent.rag.core.prompt.AgentPromptSlot;
import com.safeguard.agent.rag.core.skill.AgentSkillRegistry;
import com.safeguard.agent.rag.enums.IntentKind;
import com.safeguard.agent.rag.service.KnowledgeSearchFacade;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReActAgentProviderTest {

    private IntentNodeRegistry intentNodeRegistry;
    private McpToolRegistry mcpToolRegistry;
    private AgentPromptResolver agentPromptResolver;
    private AgentToolCatalog toolCatalog;
    private ReActAgentProvider provider;

    @BeforeEach
    void setUp() {
        intentNodeRegistry = mock(IntentNodeRegistry.class);
        mcpToolRegistry = mock(McpToolRegistry.class);
        agentPromptResolver = mock(AgentPromptResolver.class);
        when(agentPromptResolver.resolve(AgentPromptSlot.AGENT_MAIN)).thenReturn("你是 Ragent");
        when(agentPromptResolver.resolve(AgentPromptSlot.KNOWLEDGE_TOOL_DESCRIPTION))
                .thenReturn("当前 Agent 的知识库工具描述");
        when(intentNodeRegistry.listMcpToolNodes()).thenReturn(List.of(
                mcpNode("sales", "销售查询", "sales_query")));
        when(mcpToolRegistry.listAllExecutors()).thenReturn(List.of(executor("sales_query")));

        toolCatalog = spy(new AgentToolCatalog(
                mock(KnowledgeSearchFacade.class),
                mock(AgentConversationService.class),
                intentNodeRegistry,
                mcpToolRegistry,
                agentPromptResolver,
                new AgentMemoryProperties(),
                mock(AgentMemoryPipeline.class),
                mock(AgentSkillRegistry.class)));
        AgentProperties agentProperties = new AgentProperties();
        provider = new ReActAgentProvider(
                agentPromptResolver,
                toolCatalog,
                mock(OpenAIChatModel.class),
                mock(PgAgentStateStore.class),
                agentProperties,
                mock(AgentUserMemoryMiddleware.class),
                mock(AgentContextCompactionMiddleware.class),
                mock(AgentConfirmDenialMiddleware.class),
                mock(AgentSkillMaskingMiddleware.class));
    }

    @Test
    void shouldResolveToolCatalogOncePerRequest() {
        provider.getAgent();

        // 解析两次就有两份现实，指纹与 Toolkit 各信一份，中间注册表一变就长期不再自愈
        verify(toolCatalog, times(1)).resolve();
        verify(mcpToolRegistry, times(1)).listAllExecutors();
        verify(agentPromptResolver, times(1)).resolve(AgentPromptSlot.KNOWLEDGE_TOOL_DESCRIPTION);
    }

    @Test
    void shouldBuildToolkitFromTheSnapshotItsFingerprintCameFrom() {
        provider.getAgent();

        verify(toolCatalog, times(1)).buildToolkit(any(AgentToolCatalog.ResolvedCatalog.class));
    }

    @Test
    void shouldReuseCachedAgentWhenCatalogUnchanged() {
        var first = provider.getAgent();
        var second = provider.getAgent();

        assertThat(second.agent()).isSameAs(first.agent());
        assertThat(second.catalog()).isSameAs(first.catalog());
        verify(toolCatalog, times(1)).buildToolkit(any(AgentToolCatalog.ResolvedCatalog.class));
    }

    @Test
    void shouldRebuildWhenMcpToolAppears() {
        var first = provider.getAgent();
        when(mcpToolRegistry.listAllExecutors())
                .thenReturn(List.of(executor("sales_query"), executor("orders_query")));
        when(intentNodeRegistry.listMcpToolNodes()).thenReturn(List.of(
                mcpNode("sales", "销售查询", "sales_query"),
                mcpNode("orders", "订单查询", "orders_query")));

        var second = provider.getAgent();

        assertThat(second.agent()).isNotSameAs(first.agent());
        assertThat(second.catalog().displayNameOf("orders_query")).isEqualTo("订单查询");
    }

    @Test
    void shouldCarryDisplayNamesOnSnapshot() {
        var active = provider.getAgent();

        assertThat(active.catalog().displayNameOf("sales_query")).isEqualTo("销售查询");
        assertThat(active.catalog().displayNameOf(KnowledgeSearchTool.TOOL_NAME))
                .isEqualTo(KnowledgeSearchTool.DISPLAY_NAME);
        assertThat(active.catalog().displayNameOf("unknown_query")).isEqualTo("unknown_query");
    }

    private IntentNode mcpNode(String id, String name, String toolId) {
        return IntentNode.builder()
                .id(id)
                .name(name)
                .description("意图树描述")
                .kind(IntentKind.MCP)
                .mcpToolId(toolId)
                .build();
    }

    private McpToolExecutor executor(String toolId) {
        Tool tool = Tool.builder()
                .name(toolId)
                .description("MCP 服务端描述")
                .inputSchema(new JsonSchema("object", Map.of(), List.of(), false, null, null))
                .build();
        return new McpToolExecutor() {
            @Override
            public Tool getToolDefinition() {
                return tool;
            }

            @Override
            public CallToolResult execute(Map<String, Object> parameters) {
                return null;
            }
        };
    }
}

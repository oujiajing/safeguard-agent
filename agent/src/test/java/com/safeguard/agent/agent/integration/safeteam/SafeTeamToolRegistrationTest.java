package com.safeguard.agent.agent.integration.safeteam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.agent.memory.AgentMemoryPipeline;
import com.safeguard.agent.agent.memory.AgentMemoryProperties;
import com.safeguard.agent.agent.service.AgentConversationService;
import com.safeguard.agent.agent.tool.AgentToolCatalog;
import com.safeguard.agent.rag.core.intent.IntentNode;
import com.safeguard.agent.rag.core.intent.IntentNodeRegistry;
import com.safeguard.agent.rag.core.mcp.McpToolExecutor;
import com.safeguard.agent.rag.core.mcp.McpToolRegistry;
import com.safeguard.agent.rag.core.prompt.AgentPromptResolver;
import com.safeguard.agent.rag.core.prompt.AgentPromptSlot;
import com.safeguard.agent.rag.core.skill.AgentSkillRegistry;
import com.safeguard.agent.rag.enums.IntentKind;
import com.safeguard.agent.rag.service.KnowledgeSearchFacade;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeTeamToolRegistrationTest {
    @Test
    void fourToolsAreVisibleOnlyWhenRegistryAndIntentBindingsBothExist() {
        SafeTeamApiClient client = mock(SafeTeamApiClient.class);
        ObjectMapper mapper = new ObjectMapper();
        List<SafeTeamToolExecutor> executors = List.of(
                SafeTeamToolExecutor.search(client, mapper),
                SafeTeamToolExecutor.detail(client, mapper),
                SafeTeamToolExecutor.create(client, mapper),
                SafeTeamToolExecutor.issue(client, mapper));

        IntentNodeRegistry intents = mock(IntentNodeRegistry.class);
        when(intents.listMcpToolNodes()).thenReturn(executors.stream()
                .map(executor -> IntentNode.builder().id(executor.getToolId())
                        .name(executor.getToolId()).description(executor.getToolId())
                        .kind(IntentKind.MCP).mcpToolId(executor.getToolId()).build()).toList());
        McpToolRegistry registry = mock(McpToolRegistry.class);
        when(registry.listAllExecutors()).thenReturn(List.copyOf(executors));
        AgentPromptResolver prompts = mock(AgentPromptResolver.class);
        when(prompts.resolve(AgentPromptSlot.KNOWLEDGE_TOOL_DESCRIPTION)).thenReturn("知识库工具");
        AgentMemoryProperties memory = new AgentMemoryProperties();
        memory.setLongTermEnabled(false);

        AgentToolCatalog catalog = new AgentToolCatalog(
                mock(KnowledgeSearchFacade.class),
                mock(com.safeguard.agent.knowledge.service.KnowledgeDocumentService.class),
                mock(AgentConversationService.class), intents,
                registry, prompts, memory, mock(AgentMemoryPipeline.class), mock(AgentSkillRegistry.class));
        Toolkit toolkit = catalog.buildToolkit(catalog.resolve());

        assertThat(toolkit.getToolNames()).containsExactlyInAnyOrder(
                "search_knowledge", "count_knowledge_documents", "search_rectification_orders", "get_rectification_order",
                "create_rectification_order", "issue_rectification");
        assertThat(catalog.mcpToolCount()).isEqualTo(4);
    }

    @Test
    void factoryDeclaresAllFourIntentToolIds() {
        List<String> toolIds = com.safeguard.agent.rag.core.intent.IntentTreeFactory.buildIntentTree()
                .stream().flatMap(root -> flatten(root).stream())
                .map(IntentNode::getMcpToolId).filter(java.util.Objects::nonNull).toList();
        assertThat(toolIds).containsExactlyInAnyOrder(
                "sales_query", "search_rectification_orders", "get_rectification_order",
                "create_rectification_order", "issue_rectification");
    }

    private static List<IntentNode> flatten(IntentNode root) {
        java.util.ArrayList<IntentNode> all = new java.util.ArrayList<>();
        all.add(root);
        if (root.getChildren() != null) root.getChildren().forEach(child -> all.addAll(flatten(child)));
        return all;
    }
}

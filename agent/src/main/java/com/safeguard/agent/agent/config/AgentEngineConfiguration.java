package com.safeguard.agent.agent.config;

import cn.hutool.core.util.StrUtil;
import com.safeguard.agent.agent.dao.mapper.AgentStateMapper;
import com.safeguard.agent.agent.state.PgAgentStateStore;
import com.safeguard.agent.infra.config.AIModelProperties;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Agent 引擎装配：模型与状态存储
 * MCP 不走 AgentScope 自带客户端（其 MCP SDK 0.17.0 被根 pom 的 1.1.2 压制），统一桥接 rag 既有连接
 */
@Configuration
@ConditionalOnAgentEngine
@RequiredArgsConstructor
public class AgentEngineConfiguration {

    private final AgentProperties agentProperties;
    private final AIModelProperties aiModelProperties;

    @Bean
    public OpenAIChatModel agentChatModel() {
        AgentProperties.Chat chat = agentProperties.getChat();
        if (chat == null || StrUtil.isBlank(chat.getProvider()) || StrUtil.isBlank(chat.getModel())) {
            throw new IllegalStateException("agent.chat.provider / agent.chat.model 未配置");
        }

        Map<String, AIModelProperties.ProviderConfig> providers = aiModelProperties.getProviders();
        AIModelProperties.ProviderConfig provider = providers == null ? null : providers.get(chat.getProvider());
        if (provider == null) {
            throw new IllegalStateException("agent.chat.provider 在 ai.providers 中不存在: " + chat.getProvider());
        }
        String endpointPath = provider.getEndpoints() == null ? null : provider.getEndpoints().get("chat");
        if (StrUtil.isBlank(provider.getUrl()) || StrUtil.isBlank(endpointPath)) {
            throw new IllegalStateException("供应商缺少 url 或 endpoints.chat: " + chat.getProvider());
        }

        return OpenAIChatModel.builder()
                .baseUrl(provider.getUrl())
                .endpointPath(endpointPath)
                .apiKey(provider.getApiKey())
                .modelName(chat.getModel())
                .stream(true)
                // 兼容端点普遍无法同时处理 response_format 与工具调用，统一走 generate_response 兜底
                .nativeStructuredOutputWithTools(false)
                .build();
    }

    @Bean
    public PgAgentStateStore agentStateStore(AgentStateMapper agentStateMapper) {
        return new PgAgentStateStore(agentStateMapper);
    }
}

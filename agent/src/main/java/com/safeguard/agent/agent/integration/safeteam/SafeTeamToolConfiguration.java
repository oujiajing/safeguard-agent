package com.safeguard.agent.agent.integration.safeteam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.agent.config.ConditionalOnAgentEngine;
import com.safeguard.agent.rag.core.mcp.McpToolExecutor;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnAgentEngine
public class SafeTeamToolConfiguration {
    @Bean
    public McpToolExecutor searchRectificationOrders(SafeTeamApiClient client, ObjectMapper mapper) {
        return SafeTeamToolExecutor.search(client, mapper);
    }

    @Bean
    public McpToolExecutor getRectificationOrder(SafeTeamApiClient client, ObjectMapper mapper) {
        return SafeTeamToolExecutor.detail(client, mapper);
    }

    @Bean
    public SafeTeamToolExecutor createRectificationOrder(SafeTeamApiClient client, ObjectMapper mapper) {
        return SafeTeamToolExecutor.create(client, mapper);
    }

    @Bean
    public McpToolExecutor issueRectification(SafeTeamApiClient client, ObjectMapper mapper) {
        return SafeTeamToolExecutor.issue(client, mapper);
    }
}

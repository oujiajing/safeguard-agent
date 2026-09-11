package com.safeguard.agent.agent.integration.safeteam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class SafeTeamLiveIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "SAFE_TEAM_LIVE", matches = "true")
    void compiledSafeGuardToolCallsRunningSafeTeam() {
        SafeTeamIntegrationProperties properties = new SafeTeamIntegrationProperties();
        properties.setBaseUrl(System.getenv().getOrDefault("SAFE_TEAM_BASE_URL", "http://localhost:8080"));
        properties.setDevToken(System.getenv("SAFE_TEAM_DEV_TOKEN"));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SafeTeamApiClient client = new SafeTeamApiClient(properties, mapper);
        SafeTeamToolExecutor tool = SafeTeamToolExecutor.search(client, mapper);

        var result = tool.execute(Map.of("status", "PENDING_RECTIFY", "page", 1, "pageSize", 10));

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAFE_TEAM_LIVE", matches = "true")
    void developmentWriteEntryCreatesAndIssuesRealOrder() throws Exception {
        SafeTeamIntegrationProperties properties = new SafeTeamIntegrationProperties();
        properties.setBaseUrl(System.getenv().getOrDefault("SAFE_TEAM_BASE_URL", "http://localhost:8080"));
        properties.setDevToken(System.getenv("SAFE_TEAM_DEV_TOKEN"));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SafeTeamApiClient client = new SafeTeamApiClient(properties, mapper);
        SafeTeamToolExecutor create = SafeTeamToolExecutor.create(client, mapper);
        SafeTeamToolExecutor issue = SafeTeamToolExecutor.issue(client, mapper);

        var created = create.executeForDevelopment(Map.of(
                "companyId", 4, "departmentId", 101109, "teamId", 1011001,
                "businessDate", "2026-09-01",
                "items", List.of(Map.of("riskType", "Phase1", "checkItem", "safeguard live tool",
                        "hazardDescription", "真实 HTTP Tool 联调"))));
        assertThat(created.isError()).isFalse();
        JsonNode createdData = mapper.readTree(((TextContent) created.content().get(0)).text());
        String orderId = createdData.path("id").asText();

        var issued = issue.executeForDevelopment(Map.of(
                "orderId", orderId, "rectificationResponsibleUserId", 2,
                "rectificationDepartmentId", 101109, "rectificationRequirement", "完成 Phase1 联调",
                "rectificationDeadline", "2026-09-03 18:00:00", "acceptanceUserId", 3));
        assertThat(issued.isError()).isFalse();
        JsonNode issuedData = mapper.readTree(((TextContent) issued.content().get(0)).text());
        assertThat(issuedData.path("status").asText()).isEqualTo("PENDING_RECTIFY");
        assertThat(issuedData.path("version").asInt()).isEqualTo(1);
    }
}

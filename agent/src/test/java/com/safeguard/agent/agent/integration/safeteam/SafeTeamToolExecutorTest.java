package com.safeguard.agent.agent.integration.safeteam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SafeTeamToolExecutorTest {
    private MockWebServer server;
    private ObjectMapper mapper;
    private SafeTeamToolExecutor issue;
    private SafeTeamToolExecutor create;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        SafeTeamIntegrationProperties properties = new SafeTeamIntegrationProperties();
        properties.setBaseUrl(server.url("/").toString());
        properties.setDevToken("development-token");
        SafeTeamApiClient client = new SafeTeamApiClient(properties, mapper);
        issue = SafeTeamToolExecutor.issue(client, mapper);
        create = SafeTeamToolExecutor.create(client, mapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.close();
    }

    @Test
    void schemasExposeStableNamesAndDoNotExposeActionOrVersion() {
        assertThat(issue.getToolDefinition().name()).isEqualTo("issue_rectification");
        assertThat(issue.getToolDefinition().inputSchema().properties()).containsKeys(
                "orderId", "rectificationResponsibleUserId", "rectificationRequirement", "rectificationDeadline");
        assertThat(issue.getToolDefinition().inputSchema().properties()).doesNotContainKeys("action", "version");
        assertThat(issue.getToolDefinition().annotations().readOnlyHint()).isFalse();
        assertThat(create.requiresConfirmation()).isTrue();
        assertThat(create.execute(Map.of()).isError()).isTrue();
    }

    @Test
    void issueReadsLatestDetailAndUsesItsVersionInFixedAction() throws Exception {
        enqueue("{\"code\":0,\"data\":{\"id\":\"7\",\"status\":\"PENDING_ASSIGN\",\"version\":4},\"message\":\"ok\"}");
        enqueue("{\"code\":0,\"data\":{\"id\":\"7\",\"status\":\"PENDING_RECTIFY\",\"version\":5},\"message\":\"ok\"}");

        assertThat(issue.executeForDevelopment(Map.of(
                "orderId", 7,
                "rectificationResponsibleUserId", 100,
                "rectificationDepartmentId", 20,
                "rectificationRequirement", "整改",
                "rectificationDeadline", "2026-09-03 18:00:00",
                "acceptanceUserId", 101))).isNotNull();

        RecordedRequest detail = server.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest action = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(detail.getMethod()).isEqualTo("GET");
        assertThat(action.getBody().utf8()).contains("ISSUE_RECTIFICATION", "\"version\":4");
        assertThat(action.getBody().utf8()).doesNotContain("\"action\":\"issue_rectification\"");
    }

    @Test
    void issueRefusesNonPendingAssignWithoutPostingAction() throws Exception {
        enqueue("{\"code\":0,\"data\":{\"id\":\"7\",\"status\":\"RECTIFIED\",\"version\":4},\"message\":\"ok\"}");

        assertThat(issue.executeForDevelopment(Map.of(
                "orderId", 7,
                "rectificationResponsibleUserId", 100,
                "rectificationRequirement", "整改",
                "rectificationDeadline", "2026-09-03 18:00:00")).isError()).isTrue();
        assertThat(server.takeRequest(1, TimeUnit.SECONDS).getMethod()).isEqualTo("GET");
        assertThat(server.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse.Builder().code(200)
                .setHeader("Content-Type", "application/json")
                .body(body).build());
    }
}

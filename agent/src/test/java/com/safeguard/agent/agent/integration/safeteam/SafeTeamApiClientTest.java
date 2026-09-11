package com.safeguard.agent.agent.integration.safeteam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.safeguard.agent.agent.integration.safeteam.SafeTeamContracts.*;

class SafeTeamApiClientTest {
    private MockWebServer server;
    private SafeTeamApiClient client;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        SafeTeamIntegrationProperties properties = new SafeTeamIntegrationProperties();
        properties.setBaseUrl(server.url("/").toString());
        properties.setDevToken("development-token");
        properties.setRequestTimeoutMs(200);
        properties.setReadMaxRetries(1);
        client = new SafeTeamApiClient(properties, mapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.close();
    }

    @Test
    void listUsesQueryParametersAndParsesApiResponse() throws Exception {
        enqueue("{\"code\":0,\"data\":{\"items\":[],\"total\":0},\"error\":null,\"message\":\"ok\"}");

        ApiResponse<PageResult<OrderListItem>> response = client.search(
                new OrderQuery("PENDING_RECTIFY", 4L, null, 9L, null,
                        LocalDate.of(2026, 9, 1), null, 2, 50));

        assertThat(response.code()).isZero();
        assertThat(response.data().total()).isZero();
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getTarget()).contains("status=PENDING_RECTIFY", "companyId=4", "teamId=9", "page=2");
        assertThat(request.getHeaders().get("Authorization")).isEqualTo("Bearer development-token");
    }

    @Test
    void detailCreateAndActionUseExpectedMethodsAndBodies() throws Exception {
        enqueue("{\"code\":0,\"data\":{\"id\":\"7\",\"status\":\"PENDING_ASSIGN\",\"version\":0},\"error\":null,\"message\":\"ok\"}");
        assertThat(client.detail(7).data().id()).isEqualTo("7");
        assertThat(server.takeRequest().getMethod()).isEqualTo("GET");

        enqueue("{\"code\":0,\"data\":{\"id\":\"8\",\"status\":\"PENDING_ASSIGN\",\"version\":0},\"error\":null,\"message\":\"ok\"}");
        client.create(new CreateRequest(4L, 5L, 6L, LocalDate.of(2026, 9, 1),
                List.of(new CreateItem("机械", "护栏", "缺失", null, null, null))));
        RecordedRequest create = server.takeRequest();
        assertThat(create.getMethod()).isEqualTo("POST");
        assertThat(create.getBody().utf8()).contains("\"companyId\":4", "\"businessDate\":\"2026-09-01\"");

        enqueue("{\"code\":0,\"data\":{\"id\":\"7\",\"status\":\"PENDING_RECTIFY\",\"version\":1},\"error\":null,\"message\":\"ok\"}");
        client.action(7, new ActionRequest("ISSUE_RECTIFICATION", Map.of("rectificationRequirement", "整改"), null, 0));
        RecordedRequest action = server.takeRequest();
        assertThat(action.getMethod()).isEqualTo("POST");
        assertThat(action.getBody().utf8()).contains("ISSUE_RECTIFICATION", "\"version\":0");
    }

    @Test
    void httpErrorsAreSurfacedWithoutChangingBusinessMeaning() {
        for (int status : List.of(400, 401, 403, 409, 500)) {
            enqueue(errorResponse(status));
            if (status == 500) {
                enqueue(errorResponse(status));
            }
            assertThatThrownBy(() -> client.detail(1))
                    .isInstanceOf(SafeTeamApiException.class)
                    .hasMessageContaining("status-" + status);
        }
    }

    @Test
    void http200StillRejectsNonZeroApiCode() {
        enqueue("{\"code\":-1,\"data\":null,\"error\":\"业务失败\",\"message\":\"业务失败\"}");
        assertThatThrownBy(() -> client.detail(1))
                .isInstanceOf(SafeTeamApiException.class)
                .hasMessage("业务失败");
    }

    @Test
    void timeoutIsReportedAndReadMayRetryButWriteDoesNot() {
        enqueue(new MockResponse.Builder().headersDelay(500, TimeUnit.MILLISECONDS).body("{}").build());
        assertThatThrownBy(() -> client.detail(1)).isInstanceOf(SafeTeamApiException.class);
        enqueue(new MockResponse.Builder().headersDelay(500, TimeUnit.MILLISECONDS).body("{}").build());
        assertThatThrownBy(() -> client.action(1, new ActionRequest("ISSUE_RECTIFICATION", Map.of(), null, 0)))
                .isInstanceOf(SafeTeamApiException.class);
    }

    private void enqueue(String body) {
        enqueue(new MockResponse.Builder().code(200).setHeader("Content-Type", "application/json").body(body).build());
    }

    private void enqueue(MockResponse response) {
        server.enqueue(response);
    }

    private MockResponse errorResponse(int status) {
        return new MockResponse.Builder().code(status)
                .setHeader("Content-Type", "application/json")
                .body("{\"code\":-1,\"message\":\"status-" + status + "\"}").build();
    }
}

package com.safeguard.agent.agent.integration.safeteam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import com.safeguard.agent.framework.context.SafeGuardExecutionContext;

import static com.safeguard.agent.agent.integration.safeteam.SafeTeamContracts.*;

@Component
public class SafeTeamApiClient {
    private final SafeTeamIntegrationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SafeTeamApiClient(SafeTeamIntegrationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    public ApiResponse<PageResult<OrderListItem>> search(OrderQuery query) {
        query = query == null ? new OrderQuery(null, null, null, null, null, null, null, null, null) : query;
        Map<String, Object> params = new LinkedHashMap<>();
        put(params, "status", query.status());
        put(params, "companyId", query.companyId());
        put(params, "departmentId", query.departmentId());
        put(params, "teamId", query.teamId());
        put(params, "responsibleUserId", query.responsibleUserId());
        put(params, "dateStart", query.dateStart());
        put(params, "dateEnd", query.dateEnd());
        put(params, "page", query.page());
        put(params, "pageSize", query.pageSize());
        return send("GET", "/api/pingan/hazard-rectification/orders" + queryString(params), null, false,
                objectMapper.getTypeFactory().constructParametricType(
                        ApiResponse.class,
                        objectMapper.getTypeFactory().constructParametricType(PageResult.class, OrderListItem.class)));
    }

    public ApiResponse<OrderDetail> detail(long orderId) {
        return send("GET", "/api/pingan/hazard-rectification/orders/" + orderId, null, false,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, OrderDetail.class));
    }

    public ApiResponse<OrderDetail> create(CreateRequest request) {
        return send("POST", "/api/pingan/hazard-rectification/orders", request, true,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, OrderDetail.class));
    }

    public ApiResponse<OrderDetail> create(CreateRequest request, SafeGuardExecutionContext context) {
        return send("POST", "/api/pingan/hazard-rectification/orders", request, true, context,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, OrderDetail.class));
    }

    public ApiResponse<OrderDetail> findByIdempotencyKey(String key, SafeGuardExecutionContext context) {
        return send("GET", "/api/pingan/hazard-rectification/orders/ai-result?idempotencyKey="
                        + URLEncoder.encode(key, StandardCharsets.UTF_8), null, false, context,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, OrderDetail.class));
    }

    public ApiResponse<OrderDetail> action(long orderId, ActionRequest request) {
        return send("POST", "/api/pingan/hazard-rectification/orders/" + orderId + "/actions", request, true,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, OrderDetail.class));
    }

    private <T> T send(String method, String path, Object body, boolean write, JavaType responseType) {
        return send(method, path, body, write, null, responseType);
    }

    private <T> T send(String method, String path, Object body, boolean write,
                       SafeGuardExecutionContext context, JavaType responseType) {
        String token = context == null ? properties.getDevToken() : properties.getServiceToken();
        if (token == null || token.isBlank()) {
            throw new SafeTeamApiException(context == null ? "SAFE_TEAM_DEV_TOKEN 未配置" : "SAFEGUARD_SERVICE_TOKEN 未配置", 0, false);
        }
        int attempts = write ? 1 : Math.max(1, properties.getReadMaxRetries() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(normalizedBaseUrl() + path))
                        .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                        .header("Accept", "application/json");
                if (context != null) {
                    builder.header("X-Safeguard-Service-Token", token)
                            .header("X-Safeguard-Actor-User-Id", String.valueOf(context.actorUserId()))
                            .header("X-Safeguard-Trace-Id", context.traceId())
                            .header("X-Safeguard-Source-Hazard-Id", context.sourceHazardId());
                } else {
                    builder.header("Authorization", bearer(token));
                }
                if (body == null) {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.header("Content-Type", "application/json")
                            .method(method, HttpRequest.BodyPublishers.ofString(writeJson(body)));
                }
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 500 && attempt < attempts) {
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new SafeTeamApiException(errorMessage(response.body(), status), status, status >= 500);
                }
                T result = objectMapper.readValue(response.body(), responseType);
                ApiResponse<?> apiResponse = objectMapper.readValue(response.body(),
                        objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, JsonNode.class));
                if (apiResponse.code() != 0) {
                    throw new SafeTeamApiException(apiResponse.message(), status, false);
                }
                return result;
            } catch (SafeTeamApiException exception) {
                throw exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SafeTeamApiException("Safe-team 请求被中断", exception, false);
            } catch (IOException | RuntimeException exception) {
                if (attempt < attempts) {
                    continue;
                }
                throw new SafeTeamApiException("Safe-team 请求失败或超时", exception, !write);
            }
        }
        throw new SafeTeamApiException("Safe-team 请求失败", 0, !write);
    }

    private String normalizedBaseUrl() {
        String base = properties.getBaseUrl();
        if (base == null || base.isBlank()) {
            throw new SafeTeamApiException("safeguard.safe-team.base-url 未配置", 0, false);
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String bearer(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    private String writeJson(Object body) {
        try {
            return objectMapper.writer().without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new SafeTeamApiException("Safe-team 请求参数无法序列化", exception, false);
        }
    }

    private String errorMessage(String body, int status) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("message").asText();
            return message == null || message.isBlank() ? "Safe-team HTTP " + status : message;
        } catch (Exception ignored) {
            return "Safe-team HTTP " + status;
        }
    }

    private String queryString(Map<String, Object> params) {
        if (params.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder("?");
        params.forEach((key, value) -> {
            if (result.length() > 1) {
                result.append('&');
            }
            result.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            result.append('=');
            result.append(URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
        });
        return result.toString();
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}

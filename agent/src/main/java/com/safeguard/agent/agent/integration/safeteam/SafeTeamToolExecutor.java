package com.safeguard.agent.agent.integration.safeteam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.rag.core.mcp.McpToolExecutor;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import com.safeguard.agent.framework.context.SafeGuardExecutionContext;

import static com.safeguard.agent.agent.integration.safeteam.SafeTeamContracts.*;

/**
 * REST-backed executors intentionally implement the existing MCP executor contract.  The
 * bridge can therefore expose them through the existing AgentScope Toolkit without introducing
 * another tool framework.  Write tools are visible to the Agent but refuse direct execution;
 * a later Phase will replace the development gate with the persistent HITL flow.
 */
public final class SafeTeamToolExecutor implements McpToolExecutor {
    public enum Kind { SEARCH, DETAIL, CREATE, ISSUE }

    private final Kind kind;
    private final SafeTeamApiClient client;
    private final ObjectMapper objectMapper;
    private final Tool definition;

    private SafeTeamToolExecutor(Kind kind, SafeTeamApiClient client, ObjectMapper objectMapper) {
        this.kind = kind;
        this.client = client;
        this.objectMapper = objectMapper;
        this.definition = definition(kind);
    }

    public static SafeTeamToolExecutor search(SafeTeamApiClient client, ObjectMapper mapper) {
        return new SafeTeamToolExecutor(Kind.SEARCH, client, mapper);
    }

    public static SafeTeamToolExecutor detail(SafeTeamApiClient client, ObjectMapper mapper) {
        return new SafeTeamToolExecutor(Kind.DETAIL, client, mapper);
    }

    public static SafeTeamToolExecutor create(SafeTeamApiClient client, ObjectMapper mapper) {
        return new SafeTeamToolExecutor(Kind.CREATE, client, mapper);
    }

    public static SafeTeamToolExecutor issue(SafeTeamApiClient client, ObjectMapper mapper) {
        return new SafeTeamToolExecutor(Kind.ISSUE, client, mapper);
    }

    @Override
    public Tool getToolDefinition() {
        return definition;
    }

    public boolean isWrite() {
        return kind == Kind.CREATE || kind == Kind.ISSUE;
    }

    public boolean requiresConfirmation() {
        return isWrite();
    }

    /** Normal Agent/MCP path.  Write calls are deliberately blocked until HITL exists. */
    @Override
    public CallToolResult execute(Map<String, Object> parameters) {
        if (isWrite()) {
            return error("该写操作需要开发测试入口的明确确认，当前自然语言入口不会直接执行");
        }
        return executeInternal(parameters);
    }

    /** Explicit local test hook; not exposed as an Agent tool. */
    public CallToolResult executeForDevelopment(Map<String, Object> parameters) {
        if (!isWrite()) {
            return executeInternal(parameters);
        }
        return executeInternal(parameters);
    }

    public CallToolResult executeWithContext(Map<String, Object> parameters, SafeGuardExecutionContext context) {
        if (!isWrite() || context == null) {
            return error("正式 Safe-team 写入必须带可信执行上下文");
        }
        try {
            Map<String, Object> params = parameters == null ? Map.of() : parameters;
            if (kind != Kind.CREATE) {
                return error("当前正式任务创建器不支持该写操作");
            }
            CreateRequest request = objectMapper.convertValue(params, CreateRequest.class);
            return success(client.create(request, context).data());
        } catch (SafeTeamApiException exception) {
            return error(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return error("工具参数不合法");
        }
    }

    public CallToolResult findByIdempotencyKey(String key, SafeGuardExecutionContext context) {
        if (context == null || key == null || key.isBlank()) {
            return error("正式任务结果查询缺少执行上下文或幂等键");
        }
        try {
            return success(client.findByIdempotencyKey(key, context).data());
        } catch (SafeTeamApiException exception) {
            return error(exception.getMessage());
        }
    }

    private CallToolResult executeInternal(Map<String, Object> raw) {
        try {
            Map<String, Object> params = raw == null ? Map.of() : raw;
            return switch (kind) {
                case SEARCH -> success(client.search(new OrderQuery(
                        text(params, "status"), longValue(params, "companyId"),
                        longValue(params, "departmentId"), longValue(params, "teamId"),
                        longValue(params, "responsibleUserId"), dateValue(params, "dateStart"),
                        dateValue(params, "dateEnd"), integerValue(params, "page"),
                        integerValue(params, "pageSize"))).data());
                case DETAIL -> {
                    Long id = requiredLong(params, "orderId");
                    yield success(compactDetail(client.detail(id).data()));
                }
                case CREATE -> {
                    CreateRequest request = objectMapper.convertValue(params, CreateRequest.class);
                    yield success(client.create(request).data());
                }
                case ISSUE -> issue(params);
            };
        } catch (SafeTeamApiException exception) {
            return error(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return error("工具参数不合法");
        }
    }

    private CallToolResult issue(Map<String, Object> params) {
        Long id = requiredLong(params, "orderId");
        OrderDetail detail = client.detail(id).data();
        if (detail == null) {
            return error("未找到整改工单");
        }
        if (!"PENDING_ASSIGN".equals(detail.status())) {
            return error("当前工单状态为 " + detail.status() + "，只有待派发工单可以下发整改");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        copy(payload, params, "rectificationResponsibleUserId", "rectificationDepartmentId",
                "rectificationRequirement", "rectificationDeadline", "acceptanceUserId",
                "acceptanceDepartmentId");
        ActionRequest request = new ActionRequest("ISSUE_RECTIFICATION", payload, null, detail.version());
        return success(compactDetail(client.action(id, request).data()));
    }

    private Tool definition(Kind value) {
        return Tool.builder()
                .name(name(value))
                .description(description(value))
                .inputSchema(new JsonSchema("object", schemaProperties(value), required(value), false, null, null))
                .annotations(new ToolAnnotations(null, !isWriteKind(value), null, null, null, null))
                .build();
    }

    private static boolean isWriteKind(Kind value) {
        return value == Kind.CREATE || value == Kind.ISSUE;
    }

    private String name(Kind value) {
        return switch (value) {
            case SEARCH -> "search_rectification_orders";
            case DETAIL -> "get_rectification_order";
            case CREATE -> "create_rectification_order";
            case ISSUE -> "issue_rectification";
        };
    }

    private String description(Kind value) {
        return switch (value) {
            case SEARCH -> "查询 Safe-team 隐患整改工单。只返回与用户筛选条件匹配的工单摘要。";
            case DETAIL -> "查询 Safe-team 单个隐患整改工单的当前详情、明细和版本。";
            case CREATE -> "创建 Safe-team 手工隐患整改工单。operationType=WRITE; requiresConfirmation=true；当前仅允许开发测试入口明确执行。";
            case ISSUE -> "下发 Safe-team 整改工单。operationType=WRITE; requiresConfirmation=true；执行器固定使用 ISSUE_RECTIFICATION，并自动读取最新 version。";
        };
    }

    private Map<String, Object> schemaProperties(Kind value) {
        Map<String, Object> p = new LinkedHashMap<>();
        switch (value) {
            case SEARCH -> {
                p.put("status", string("状态，例如 PENDING_RECTIFY"));
                p.put("companyId", integer("公司 ID"));
                p.put("departmentId", integer("部门 ID"));
                p.put("teamId", integer("班组 ID"));
                p.put("responsibleUserId", integer("整改责任人 ID"));
                p.put("dateStart", string("业务日期开始，yyyy-MM-dd"));
                p.put("dateEnd", string("业务日期结束，yyyy-MM-dd"));
                p.put("page", integer("页码"));
                p.put("pageSize", integer("每页数量，最大 100"));
            }
            case DETAIL -> p.put("orderId", integer("整改工单 ID"));
            case CREATE -> {
                p.put("companyId", integer("公司 ID"));
                p.put("departmentId", integer("部门 ID"));
                p.put("teamId", integer("班组 ID"));
                p.put("businessDate", string("业务日期，yyyy-MM-dd"));
                p.put("items", Map.of("type", "array", "description", "隐患明细", "items", Map.of(
                        "type", "object", "additionalProperties", false,
                        "properties", Map.of(
                                "riskType", string("风险类型"),
                                "checkItem", string("检查项"),
                                "hazardDescription", string("隐患描述"),
                                "beforePhoto", string("整改前照片 URL，可选"),
                                "beforeVideo", string("整改前视频 URL，可选"),
                                "defaultFollowUpPlan", string("默认跟进计划，可选")),
                        "required", List.of("riskType", "checkItem", "hazardDescription"))));
            }
            case ISSUE -> {
                p.put("orderId", integer("整改工单 ID"));
                p.put("rectificationResponsibleUserId", integer("整改责任人 ID"));
                p.put("rectificationDepartmentId", integer("整改部门 ID，可选"));
                p.put("rectificationRequirement", string("整改要求"));
                p.put("rectificationDeadline", string("整改期限，yyyy-MM-dd HH:mm:ss"));
                p.put("acceptanceUserId", integer("验收人 ID，可选"));
                p.put("acceptanceDepartmentId", integer("验收部门 ID，可选"));
            }
        }
        return p;
    }

    private List<String> required(Kind value) {
        return switch (value) {
            case SEARCH -> List.of();
            case DETAIL -> List.of("orderId");
            case CREATE -> List.of("companyId", "departmentId", "teamId", "businessDate", "items");
            case ISSUE -> List.of("orderId", "rectificationResponsibleUserId", "rectificationRequirement", "rectificationDeadline");
        };
    }

    private Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private void copy(Map<String, Object> target, Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key) && source.get(key) != null) {
                target.put(key, source.get(key));
            }
        }
    }

    private Long requiredLong(Map<String, Object> params, String key) {
        Long value = longValue(params, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " required");
        }
        return value;
    }

    private Long longValue(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; }
    }

    private Integer integerValue(Map<String, Object> params, String key) {
        Long value = longValue(params, key);
        return value == null ? null : value.intValue();
    }

    private String text(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private LocalDate dateValue(Map<String, Object> params, String key) {
        String value = text(params, key);
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private CallToolResult success(Object value) {
        try {
            return CallToolResult.builder().content(List.of(new TextContent(objectMapper.writeValueAsString(value)))).isError(false).build();
        } catch (JsonProcessingException exception) {
            return error("Safe-team 返回结果无法解析");
        }
    }

    private Map<String, Object> compactDetail(OrderDetail detail) {
        if (detail == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "id", detail.id());
        put(result, "orderNo", detail.orderNo());
        put(result, "status", detail.status());
        put(result, "statusLabel", detail.statusLabel());
        put(result, "company", detail.company());
        put(result, "department", detail.department());
        put(result, "team", detail.team());
        put(result, "businessDate", detail.businessDate());
        put(result, "rectificationResponsibleUserId", detail.rectificationResponsibleUserId());
        put(result, "rectificationRequirement", detail.rectificationRequirement());
        put(result, "rectificationDeadline", detail.rectificationDeadline());
        put(result, "acceptanceUserId", detail.acceptanceUserId());
        put(result, "version", detail.version());
        List<Map<String, Object>> items = new ArrayList<>();
        if (detail.items() != null) {
            for (OrderItem item : detail.items()) {
                Map<String, Object> compact = new LinkedHashMap<>();
                put(compact, "id", item.id());
                put(compact, "riskType", item.riskType());
                put(compact, "checkItem", item.checkItem());
                put(compact, "hazardDescription", item.hazardDescription());
                put(compact, "rectificationStatus", item.rectificationStatus());
                items.add(compact);
            }
        }
        result.put("items", items);
        return result;
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private CallToolResult error(String message) {
        return CallToolResult.builder().content(List.of(new TextContent(Objects.toString(message, "Safe-team 调用失败")))).isError(true).build();
    }
}

package com.safeguard.agent.agent.integration.safeteam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.agent.config.ConditionalOnAgentEngine;
import com.safeguard.agent.rag.eval.HazardAssessment;
import com.safeguard.agent.rag.eval.RectificationTaskCreator;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEngine
@ConditionalOnProperty(prefix = "safeguard.safe-team", name = "enabled", havingValue = "true")
public class SafeTeamRectificationTaskCreator implements RectificationTaskCreator {
    private final SafeTeamToolExecutor executor;
    private final ObjectMapper mapper;
    public SafeTeamRectificationTaskCreator(
            @Qualifier("createRectificationOrder") SafeTeamToolExecutor executor, ObjectMapper mapper) {
        this.executor = executor;
        this.mapper = mapper;
    }
    @Override public TaskCreationResult create(HazardAssessment a, TaskCreationContext context) {
        if (context.companyId() == null) return new TaskCreationResult(false, null, null, "公司不能为空");
        if (context.departmentId() == null) return new TaskCreationResult(false, null, null, "部门不能为空");
        if (context.teamId() == null) return new TaskCreationResult(false, null, null, "班组不能为空");
        if (context.executionContext() == null || context.idempotencyKey() == null || context.idempotencyKey().isBlank()) {
            return new TaskCreationResult(false, null, null, "正式任务创建缺少执行上下文或幂等键");
        }
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("companyId", context.companyId());
        params.put("departmentId", context.departmentId());
        params.put("teamId", context.teamId());
        params.put("businessDate", LocalDate.now());
        params.put("items", List.of(Map.of(
                "riskType", a.category(), "checkItem", a.riskSummary(), "hazardDescription", a.hazardDescription(),
                "defaultFollowUpPlan", String.join("；", a.rectificationSuggestions()))));
        params.put("aiSourceHazardId", context.executionContext().sourceHazardId());
        params.put("aiAssessmentId", a.assessmentId());
        params.put("aiConfirmationId", a.assessmentId());
        params.put("aiIdempotencyKey", context.idempotencyKey());
        CallToolResult result = executor.executeWithContext(params, context.executionContext());
        String text = result.content().stream().filter(TextContent.class::isInstance).map(x -> ((TextContent)x).text()).findFirst().orElse("");
        if (result.isError()) return new TaskCreationResult(false, null, null, text);
        try { JsonNode root = mapper.readTree(text); return new TaskCreationResult(true, root.path("id").asText(null), root.path("status").asText("CREATED"), null); }
        catch (Exception e) { return new TaskCreationResult(false, null, null, "Safe-team 返回结果无法解析"); }
    }

    @Override public TaskCreationResult findExisting(TaskCreationContext context) {
        if (context == null || context.executionContext() == null || context.idempotencyKey() == null) {
            return new TaskCreationResult(false, null, null, "未查询到既有任务");
        }
        try {
            CallToolResult result = executor.findByIdempotencyKey(context.idempotencyKey(), context.executionContext());
            if (result.isError()) return new TaskCreationResult(false, null, null, "未查询到既有任务");
            String text = result.content().stream().filter(TextContent.class::isInstance)
                    .map(x -> ((TextContent) x).text()).findFirst().orElse("");
            JsonNode root = mapper.readTree(text);
            if (root.isNull() || root.isMissingNode() || root.path("id").asText("").isBlank()) {
                return new TaskCreationResult(false, null, null, "未查询到既有任务");
            }
            return new TaskCreationResult(true, root.path("id").asText(), root.path("status").asText("CREATED"), null);
        } catch (Exception exception) {
            return new TaskCreationResult(false, null, null, "任务结果查询失败");
        }
    }
}

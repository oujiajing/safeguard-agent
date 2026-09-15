package com.safeguard.agent.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.agent.attachment.AgentImageAttachmentService;
import com.safeguard.agent.agent.config.ConditionalOnAgentEngine;
import com.safeguard.agent.agent.confirm.AgentWriteIntentGuardMiddleware;
import com.safeguard.agent.agent.integration.safeteam.SafeTeamApiClient;
import com.safeguard.agent.agent.integration.safeteam.SafeTeamContracts.OrganizationSelection;
import com.safeguard.agent.agent.integration.safeteam.SafeTeamIntegrationProperties;
import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import com.safeguard.agent.rag.eval.HazardAssessmentResult;
import com.safeguard.agent.rag.eval.HazardAssessmentService;
import com.safeguard.agent.rag.eval.RectificationTaskCreator;
import com.safeguard.agent.rag.eval.VisualHazardAnalysisRequest;
import com.safeguard.agent.rag.eval.VisualHazardContext;
import com.safeguard.agent.rag.eval.VisualHazardService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Builds the domain tools that make safety handling part of the universal Agent chat. */
@Component
@ConditionalOnAgentEngine
@RequiredArgsConstructor
public class SafetyAgentToolFactory {

    public static final String ASSESS_TOOL = "assess_safety_hazard";
    public static final String VISUAL_TOOL = "analyze_visual_hazard";
    public static final String CREATE_TOOL = "create_rectification_from_assessment";

    private final HazardAssessmentService assessmentService;
    private final VisualHazardService visualHazardService;
    private final AgentImageAttachmentService attachmentService;
    private final ObjectMapper objectMapper;
    private final SafeTeamApiClient safeTeamApiClient;
    private final SafeTeamIntegrationProperties safeTeamProperties;

    public List<AgentTool> createTools() {
        List<AgentTool> tools = new java.util.ArrayList<>(List.of(new HazardAssessmentTool(), new VisualHazardTool()));
        if (safeTeamProperties.isEnabled()) {
            tools.add(new CreateRectificationTool());
        }
        return List.copyOf(tools);
    }

    private final class HazardAssessmentTool implements AgentTool {
        @Override public String getName() { return ASSESS_TOOL; }
        @Override public String getDescription() {
            return "评估施工现场隐患：先检索法规证据，再生成风险说明、整改措施和验收标准。"
                    + "用户描述具体施工隐患或确认了图片中的某个候选后调用；普通知识问答不要调用。"
                    + (safeTeamProperties.isEnabled()
                    ? "返回 assessmentId 后，如用户要求创建整改工单，再调用创建工具。"
                    : "返回 assessmentId 供业务系统后续处理，当前 Agent 不创建业务工单。");
        }
        @Override public Map<String, Object> getParameters() {
            return objectSchema(Map.of("hazard_description", stringField("隐患描述", "完整、独立的现场隐患描述")),
                    List.of("hazard_description"));
        }
        @Override public boolean isReadOnly() { return true; }
        @Override public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.fromCallable(() -> {
                String description = requiredText(param, "hazard_description");
                HazardAssessmentResult result = assessmentService.assess(description);
                return success(param, objectMapper.writeValueAsString(result));
            }).subscribeOn(Schedulers.boundedElastic()).onErrorResume(error -> Mono.just(failure(param, error.getMessage())));
        }
    }

    private final class VisualHazardTool implements AgentTool {
        @Override public String getName() { return VISUAL_TOOL; }
        @Override public String getDescription() {
            return "读取用户本轮上传的现场图片，提取可观察的隐患候选、证据、置信度和人工复核标记。"
                    + "必须使用消息中给出的 attachment_id；得到候选后先让用户确认具体候选，不能直接创建任务。";
        }
        @Override public Map<String, Object> getParameters() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("attachment_id", stringField("图片附件 ID", "用户本轮上传后由系统提供的附件 ID"));
            fields.put("description", stringField("补充说明", "用户对现场图片的补充说明，可为空"));
            return objectSchema(fields, List.of("attachment_id"));
        }
        @Override public boolean isReadOnly() { return true; }
        @Override public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.fromCallable(() -> {
                RuntimeContext runtime = requireRuntime(param);
                String attachmentId = requiredText(param, "attachment_id");
                AgentImageAttachmentService.ImageAttachment attachment =
                        attachmentService.requireOwned(attachmentId, runtime.getUserId());
                SafeGuardExecutionContext context = executionContext(runtime.getUserId(), "image-" + attachmentId);
                String description = optionalText(param, "description");
                VisualHazardContext result = visualHazardService.analyze(new VisualHazardAnalysisRequest(
                        description, Base64.getEncoder().encodeToString(attachment.bytes()), context));
                return success(param, objectMapper.writeValueAsString(result));
            }).subscribeOn(Schedulers.boundedElastic()).onErrorResume(error -> Mono.just(failure(param, error.getMessage())));
        }
    }

    private final class CreateRectificationTool extends ToolBase implements AgentTool {
        private CreateRectificationTool() {
            super(ToolBase.builder().name(CREATE_TOOL)
                    .description("根据已完成且有法规证据的隐患评估创建整改工单。该操作会写入业务系统，必须先向用户展示参数并等待确认。")
                    .inputSchema(createSchema()).readOnly(false));
        }
        @Override public Mono<PermissionDecision> checkPermissions(Map<String, Object> input, PermissionContextState state) {
            return Mono.just(PermissionDecision.ask("创建整改工单前需要用户明确确认"));
        }
        @Override public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.fromCallable(() -> {
                RuntimeContext runtime = requireRuntime(param);
                if (AgentWriteIntentGuardMiddleware.isWriteBlocked(runtime)) {
                    return failure(param, "用户已明确要求仅评估/查询，本次写操作未执行");
                }
                String assessmentId = requiredText(param, "assessment_id");
                String companyName = requiredText(param, "company_name");
                String departmentName = requiredText(param, "department_name");
                String teamName = requiredText(param, "team_name");
                OrganizationSelection selection = safeTeamApiClient.resolveOrganization(companyName, departmentName, teamName);
                SafeGuardExecutionContext context = executionContext(runtime.getUserId(), assessmentId);
                RectificationTaskCreator.TaskCreationContext taskContext = new RectificationTaskCreator.TaskCreationContext(
                        selection.companyId(), selection.departmentId(), selection.teamId(),
                        "agent:" + runtime.getUserId() + ":" + assessmentId, context);
                HazardAssessmentService.ConfirmationResult result = assessmentService.confirm(assessmentId, taskContext);
                boolean failed = !"TASK_CREATED".equals(result.status()) && !"ALREADY_CREATED".equals(result.status());
                String json = objectMapper.writeValueAsString(result);
                return failed ? failure(param, json) : success(param, json);
            }).subscribeOn(Schedulers.boundedElastic()).onErrorResume(error -> Mono.just(failure(param, error.getMessage())));
        }
    }

    private static Map<String, Object> createSchema() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("assessment_id", stringField("评估 ID", "隐患评估工具返回的 assessmentId"));
        fields.put("company_name", stringField("公司名称", "整改工单所属公司中文名称"));
        fields.put("department_name", stringField("部门名称", "整改责任部门中文名称"));
        fields.put("team_name", stringField("班组名称", "整改责任班组中文名称"));
        return objectSchema(fields, List.of("assessment_id", "company_name", "department_name", "team_name"));
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
    }

    private static Map<String, Object> stringField(String title, String description) {
        return Map.of("type", "string", "title", title, "description", description);
    }

    private static Map<String, Object> integerField(String title, String description) {
        return Map.of("type", "integer", "title", title, "description", description);
    }

    private static RuntimeContext requireRuntime(ToolCallParam param) {
        if (param == null || param.getRuntimeContext() == null) throw new IllegalArgumentException("缺少会话上下文");
        return param.getRuntimeContext();
    }

    private static String requiredText(ToolCallParam param, String key) {
        Object value = param == null || param.getInput() == null ? null : param.getInput().get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) throw new IllegalArgumentException(key + " 不能为空");
        return text;
    }

    private static String optionalText(ToolCallParam param, String key) {
        Object value = param == null || param.getInput() == null ? null : param.getInput().get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Long requiredLong(ToolCallParam param, String key) {
        String value = requiredText(param, key);
        try { return Long.valueOf(value); } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " 必须是数字");
        }
    }

    private static SafeGuardExecutionContext executionContext(String userId, String sourceHazardId) {
        long actor;
        try { actor = Long.parseLong(userId); } catch (NumberFormatException error) {
            throw new IllegalArgumentException("当前用户 ID 无法用于业务追踪");
        }
        return new SafeGuardExecutionContext(actor, null, null, null, sourceHazardId, "agent-" + UUID.randomUUID());
    }

    private static ToolResultBlock success(ToolCallParam param, String text) {
        return result(param, text, false);
    }

    private static ToolResultBlock failure(ToolCallParam param, String text) {
        return result(param, text == null ? "工具执行失败" : text, true);
    }

    private static ToolResultBlock result(ToolCallParam param, String text, boolean error) {
        String id = param == null || param.getToolUseBlock() == null ? null : param.getToolUseBlock().getId();
        String name = param == null || param.getToolUseBlock() == null ? null : param.getToolUseBlock().getName();
        return ToolResultBlock.builder().id(id).name(name).output(TextBlock.builder().text(text).build())
                .state(error ? ToolResultState.ERROR : ToolResultState.SUCCESS).build();
    }
}

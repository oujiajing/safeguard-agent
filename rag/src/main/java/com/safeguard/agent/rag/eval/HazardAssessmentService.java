package com.safeguard.agent.rag.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.framework.convention.ChatMessage;
import com.safeguard.agent.framework.convention.ChatRequest;
import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import com.safeguard.agent.infra.chat.LLMService;
import com.safeguard.agent.legal.model.LegalEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * SafeGuard Phase 3 composition layer.  It consumes the existing legal RAG facade and
 * returns a proposal; it deliberately does not execute Safe-team write tools.
 */
@Service
public class HazardAssessmentService {

    private final LegalAnswerService legalAnswerService;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    private final HazardAssessmentRepository repository;
    private final RectificationTaskCreator taskCreator;

    public HazardAssessmentService(LegalAnswerService legalAnswerService, LLMService llmService, ObjectMapper objectMapper) {
        this(legalAnswerService, llmService, objectMapper, new InMemoryHazardAssessmentRepository(), (assessment, context) -> new RectificationTaskCreator.TaskCreationResult(false, null, null, "未配置 Safe-team 执行器"));
    }

    @Autowired
    public HazardAssessmentService(LegalAnswerService legalAnswerService, LLMService llmService, ObjectMapper objectMapper,
            HazardAssessmentRepository repository, RectificationTaskCreator taskCreator) {
        this.legalAnswerService = legalAnswerService; this.llmService = llmService; this.objectMapper = objectMapper;
        this.repository = repository; this.taskCreator = taskCreator;
    }

    public HazardAssessmentResult assess(String hazardDescription) {
        String hazard = requireHazard(hazardDescription);
        LegalAnswerResponse legal = legalAnswerService.answer(hazard);
        if (legal.evidence().isEmpty()) {
            HazardAssessmentResult result = new HazardAssessmentResult(hazard, classify(hazard), "待核实",
                    LegalAnswerService.NO_EVIDENCE, List.of(), List.of("补充现场照片、位置、作业类型和责任班组后再评估"), proposal(), UUID.randomUUID().toString());
            repository.save(new HazardAssessment(result.assessmentId(), hazard, result.category(), result.riskLevel(), result.riskExplanation(), result.suggestion(), List.of(), result.evidence(), "CONFIRMATION_REQUIRED", result.action(), null, null, null, Instant.now(), null, List.of(new HazardAssessment.TraceStep("WAIT_CONFIRM", "无 Evidence，等待补充材料"))));
            return result;
        }

        String generated = llmService.chat(ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system("你是施工安全隐患整改助手。只能依据 Evidence 输出 JSON，不得编造法规、标准号、条款号或业务字段。JSON 字段必须为 riskExplanation(字符串)、suggestion(字符串数组)、acceptanceCriteria(字符串数组)。suggestion 必须是可执行整改措施，acceptanceCriteria 必须是现场验收标准。"),
                        ChatMessage.user(prompt(hazard, legal.evidence()))))
                .temperature(0D).topP(1D).thinking(false).build());
        Advice advice = parseAdvice(generated, legal.answer());
        HazardAssessmentResult result = new HazardAssessmentResult(hazard, classify(hazard), riskLevel(hazard), advice.riskExplanation(),
                legal.evidence(), advice.suggestion(), proposal(), UUID.randomUUID().toString());
        List<String> criteria = advice.suggestion().stream().filter(s -> s.startsWith("验收标准：")).map(s -> s.substring(5)).toList();
        HazardAssessment assessment = new HazardAssessment(result.assessmentId(), result.hazard(), result.category(), result.riskLevel(), result.riskExplanation(),
                result.suggestion(), criteria, result.evidence(), HazardAssessment.Status.CONFIRMATION_REQUIRED.name(), result.action(), null, null, null, Instant.now(), null,
                List.of(new HazardAssessment.TraceStep("UNDERSTAND_HAZARD", "识别为" + result.category()), new HazardAssessment.TraceStep("RETRIEVE_EVIDENCE", "检索施工安全法规依据"), new HazardAssessment.TraceStep("GENERATE_SUGGESTION", "生成整改建议"), new HazardAssessment.TraceStep("WAIT_CONFIRM", "等待用户确认创建整改任务")));
        repository.save(assessment);
        return result;
    }

    public HazardAssessmentResult assess(String hazardDescription, SafeGuardExecutionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("缺少可信 SafeGuard 执行上下文");
        }
        return assess(hazardDescription);
    }

    public HazardAssessmentResult createAssessment(String hazardDescription) { return assess(hazardDescription); }
    public HazardAssessment get(String id) { return repository.find(id); }
    public ConfirmationResult confirm(String id) {
        return confirm(id, new RectificationTaskCreator.TaskCreationContext(null, null, null));
    }
    public ConfirmationResult confirm(String id, RectificationTaskCreator.TaskCreationContext context) {
        HazardAssessment a = repository.find(id);
        if (a == null) return new ConfirmationResult("NOT_FOUND", null, "评估不存在");
        if (HazardAssessment.Status.TASK_CREATED.name().equals(a.status())) return new ConfirmationResult("ALREADY_CREATED", a.taskId(), null);
        if (!HazardAssessment.Status.CONFIRMATION_REQUIRED.name().equals(a.status())) return new ConfirmationResult("INVALID_STATUS", a.taskId(), "当前状态不允许确认");
        if (a.evidence() == null || a.evidence().isEmpty()) { repository.markFailed(id, "无 Evidence，禁止创建任务"); return new ConfirmationResult("FAILED", null, "无 Evidence，禁止创建任务"); }
        if (context != null && context.executionContext() != null && context.idempotencyKey() != null) {
            RectificationTaskCreator.TaskCreationResult existing = taskCreator.findExisting(context);
            if (existing.success()) {
                repository.markTaskCreated(id, existing.taskId(), existing.taskStatus());
                return new ConfirmationResult("TASK_CREATED", existing.taskId(), null);
            }
        }
        if (!repository.markConfirmed(id)) return new ConfirmationResult("ALREADY_CREATED", a.taskId(), null);
        HazardAssessment confirmed = repository.find(id);
        RectificationTaskCreator.TaskCreationResult created = taskCreator.create(confirmed, context);
        if (created.success()) {
            repository.markTaskCreated(id, created.taskId(), created.taskStatus());
            HazardAssessment latest = repository.find(id);
            repository.update(withTrace(latest, new HazardAssessment.TraceStep("TOOL_CALL", "调用 create_rectification_order"), new HazardAssessment.TraceStep("TASK_RESULT", "整改任务已创建：" + created.taskId())));
            return new ConfirmationResult("TASK_CREATED", created.taskId(), null);
        }
        repository.markFailed(id, created.errorReason());
        HazardAssessment latest = repository.find(id);
        repository.update(withTrace(latest, new HazardAssessment.TraceStep("TOOL_CALL", "调用 create_rectification_order"), new HazardAssessment.TraceStep("TASK_RESULT", "整改任务创建失败：" + created.errorReason())));
        return new ConfirmationResult("FAILED", null, created.errorReason());
    }
    private HazardAssessment withTrace(HazardAssessment a, HazardAssessment.TraceStep... steps) {
        List<HazardAssessment.TraceStep> trace = new ArrayList<>(a.trace() == null ? List.of() : a.trace());
        trace.addAll(List.of(steps));
        return new HazardAssessment(a.assessmentId(), a.hazardDescription(), a.category(), a.riskLevel(), a.riskSummary(), a.rectificationSuggestions(), a.acceptanceCriteria(), a.evidence(), a.status(), a.toolProposal(), a.taskId(), a.taskStatus(), a.errorReason(), a.createdTime(), a.confirmedTime(), trace);
    }
    public record ConfirmationResult(String status, String taskId, String errorReason) {}

    private String prompt(String hazard, List<LegalEvidence> evidence) {
        StringBuilder result = new StringBuilder("隐患：").append(hazard).append("\nEvidence：\n");
        for (LegalEvidence item : evidence) {
            result.append('[').append(item.evidenceId()).append("] ")
                    .append(item.documentTitle()).append(' ').append(item.standardNo()).append(' ')
                    .append(item.clauseNo()).append("：").append(item.content()).append('\n');
        }
        return result.append("只返回合法 JSON，不要 Markdown 代码围栏。风险说明、整改措施和验收标准都必须能由上述 Evidence 支持。").toString();
    }

    private Advice parseAdvice(String generated, String fallback) {
        try {
            JsonNode root = objectMapper.readTree(generated);
            String explanation = text(root, "riskExplanation", fallback);
            List<String> measures = strings(root, "suggestion");
            List<String> criteria = strings(root, "acceptanceCriteria");
            List<String> combined = new ArrayList<>(measures);
            criteria.forEach(item -> combined.add("验收标准：" + item));
            if (!combined.isEmpty()) return new Advice(explanation, combined);
        } catch (Exception ignored) {
            // LLM 输出异常时仍返回可审计的原文建议，不伪造法规结论。
        }
        return new Advice(fallback, List.of("依据检索证据制定整改方案，并由现场安全管理人员复核后执行"));
    }

    private List<String> strings(JsonNode root, String field) {
        List<String> result = new ArrayList<>();
        JsonNode values = root.path(field);
        if (values.isArray()) values.forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText()); });
        return result;
    }

    private String text(JsonNode root, String field, String fallback) {
        String value = root.path(field).asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private HazardAssessmentResult.Action proposal() {
        // An assessment is evidence and a proposal only. It is not user authorization to write.
        return new HazardAssessmentResult.Action(false, false, null, "ASSESSMENT_ONLY");
    }

    private String classify(String hazard) {
        String value = hazard.toLowerCase(Locale.ROOT);
        if (value.contains("脚手架") || value.contains("剪刀撑")) return "脚手架";
        if (value.contains("配电") || value.contains("电箱") || value.contains("临电")) return "临时用电";
        if (value.contains("基坑") || value.contains("支护")) return "基坑工程";
        if (value.contains("吊装") || value.contains("起重")) return "起重吊装";
        if (value.contains("高处") || value.contains("安全带") || value.contains("洞口")) return "高处作业";
        if (value.contains("临边") || value.contains("防护栏")) return "临边防护";
        return "施工安全综合隐患";
    }

    private String riskLevel(String hazard) {
        String value = hazard.toLowerCase(Locale.ROOT);
        if (value.contains("坍塌") || value.contains("触电") || value.contains("吊装") || value.contains("临边") || value.contains("高处")) return "高";
        return "中";
    }

    private String requireHazard(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("hazardDescription 不能为空");
        return value.trim();
    }

    private record Advice(String riskExplanation, List<String> suggestion) {}
}

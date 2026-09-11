package com.safeguard.agent.rag.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class VisualHazardService {
    private static final String SYSTEM_PROMPT = "只输出一个JSON对象，不要Markdown。字段：scene:string，hazardCandidates:array。每个候选字段：candidateId不要输出、hazardType:string、operationObject:string、description:string、visibleEvidence:array of strings、potentialRisk:string、judgement只能CONFIRMED/SUSPECTED/UNKNOWN、confidence:number 0到1、needsManualVerification:boolean。只写图片可观察事实；用户文字只能保留为userProvidedContext，不能写入visibleEvidence。无法由图片确认的尺寸、证件、检测值、荷载、接地电阻只能UNKNOWN或SUSPECTED，needsManualVerification=true。不得生成法规、整改任务、责任人或期限。";

    private final VisualHazardProperties properties;
    private final ObjectMapper mapper;
    private final HazardAssessmentService assessmentService;
    private final HttpClient httpClient;
    private final Map<String, VisualHazardContext> analyses = new ConcurrentHashMap<>();

    public VisualHazardService(VisualHazardProperties properties, ObjectMapper mapper,
                               HazardAssessmentService assessmentService) {
        this.properties = properties;
        this.mapper = mapper;
        this.assessmentService = assessmentService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public VisualHazardContext analyze(VisualHazardAnalysisRequest request) {
        if (request.executionContext() == null) throw new IllegalArgumentException("缺少可信执行上下文");
        byte[] image = decodeImage(request.imageBase64());
        String content = callVlm(image, request.description());
        VisualHazardContext context = parse(content, request.description());
        analyses.put(context.analysisId(), context);
        return context;
    }

    public VisualConfirmationResult confirm(String analysisId, VisualHazardConfirmationRequest request,
                                            SafeGuardExecutionContext executionContext) {
        VisualHazardContext context = analyses.get(analysisId);
        if (context == null) throw new IllegalArgumentException("视觉分析不存在");
        Map<String, VisualHazardContext.Candidate> candidates = new HashMap<>();
        context.hazardCandidates().forEach(candidate -> candidates.put(candidate.candidateId(), candidate));
        List<HazardAssessmentResult> assessments = new ArrayList<>();
        for (VisualHazardConfirmationRequest.ConfirmedCandidate item : request.candidates() == null ? List.<VisualHazardConfirmationRequest.ConfirmedCandidate>of() : request.candidates()) {
            VisualHazardContext.Candidate original = candidates.get(item.candidateId());
            if (original == null) throw new IllegalArgumentException("candidateId 不属于本次分析");
            String description = item.description() == null || item.description().isBlank() ? original.description() : item.description();
            assessments.add(assessmentService.assess(description, executionContext));
        }
        return new VisualConfirmationResult(analysisId, assessments, "CONFIRMATION_REQUIRED");
    }

    private byte[] decodeImage(String value) {
        String normalized = value.contains(",") ? value.substring(value.indexOf(',') + 1) : value;
        byte[] bytes = Base64.getDecoder().decode(normalized);
        if (bytes.length == 0 || bytes.length > properties.getMaxImageBytes()) throw new IllegalArgumentException("图片大小不合法");
        return bytes;
    }

    private String callVlm(byte[] image, String description) {
        try {
            Map<String, Object> user = new HashMap<>();
            user.put("role", "user");
            user.put("content", description == null ? "仅依据图片提取可观察安全现象；无法判断则UNKNOWN。" : description);
            user.put("images", List.of(Base64.getEncoder().encodeToString(image)));
            Map<String, Object> body = new HashMap<>();
            body.put("model", properties.getModel());
            body.put("stream", false);
            body.put("think", false);
            body.put("format", "json");
            body.put("options", Map.of("temperature", 0, "num_ctx", 4096, "num_predict", 512));
            body.put("messages", List.of(Map.of("role", "system", "content", SYSTEM_PROMPT), user));
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl().replaceAll("/$", "") + "/api/chat"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("VLM HTTP " + response.statusCode());
            return mapper.readTree(response.body()).path("message").path("content").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("VLM 调用失败", exception);
        }
    }

    private VisualHazardContext parse(String raw, String userContext) {
        try {
            JsonNode root = mapper.readTree(raw);
            List<VisualHazardContext.Candidate> candidates = new ArrayList<>();
            JsonNode items = root.path("hazardCandidates");
            if (!items.isArray()) throw new IllegalArgumentException("VLM hazardCandidates 必须是数组");
            int index = 0;
            for (JsonNode item : items) {
                if (index++ >= properties.getMaxCandidates()) break;
                String judgement = item.path("judgement").asText("UNKNOWN").toUpperCase();
                if (!List.of("CONFIRMED", "SUSPECTED", "UNKNOWN").contains(judgement)) throw new IllegalArgumentException("VLM judgement 不合法");
                List<String> evidence = new ArrayList<>();
                if (!item.path("visibleEvidence").isArray()) throw new IllegalArgumentException("visibleEvidence 必须是数组");
                item.path("visibleEvidence").forEach(node -> { if (node.isTextual()) evidence.add(node.asText()); });
                double confidence = Math.max(0, Math.min(1, item.path("confidence").asDouble(0)));
                boolean manual = item.path("needsManualVerification").asBoolean(true) || !"CONFIRMED".equals(judgement);
                candidates.add(new VisualHazardContext.Candidate(UUID.randomUUID().toString(), item.path("hazardType").asText("UNKNOWN"), item.path("operationObject").asText("UNKNOWN"), item.path("description").asText("UNKNOWN"), evidence, item.path("potentialRisk").asText("UNKNOWN"), judgement, confidence, manual));
            }
            return new VisualHazardContext(UUID.randomUUID().toString(), root.path("scene").asText("UNKNOWN"), userContext, properties.getModel(), Instant.now(), candidates);
        } catch (Exception exception) {
            throw new IllegalArgumentException("VLM结构化输出不合法", exception);
        }
    }

    public record VisualConfirmationResult(String analysisId, List<HazardAssessmentResult> assessments, String status) {}
}

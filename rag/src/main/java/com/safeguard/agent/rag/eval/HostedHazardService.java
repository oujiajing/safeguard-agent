package com.safeguard.agent.rag.eval;

import com.safeguard.agent.legal.model.LegalEvidence;
import com.safeguard.agent.rag.dto.SubQuestionRetrievalTrace;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Business-host aggregation layer. It is deliberately assessment-only: no confirmation or task
 * creator is injected or called here.
 */
@Service
public class HostedHazardService {
    private final VisualHazardService visualHazardService;
    private final HazardAssessmentService assessmentService;
    private final HostedVisualRunRepository visualRunRepository;
    private final HostedAssessmentRepository assessmentRepository;

    public HostedHazardService(VisualHazardService visualHazardService,
                               HazardAssessmentService assessmentService,
                               HostedVisualRunRepository visualRunRepository,
                               HostedAssessmentRepository assessmentRepository) {
        this.visualHazardService = visualHazardService;
        this.assessmentService = assessmentService;
        this.visualRunRepository = visualRunRepository;
        this.assessmentRepository = assessmentRepository;
    }

    public HostedVisualRun analyze(HostedVisualAnalysisRequest request) {
        requireContext(request.executionContext());
        String runId = UUID.randomUUID().toString();
        Map<String, HostedVisualRun.HostedCandidate> deduplicated = new LinkedHashMap<>();
        String model = "";
        for (HostedVisualAnalysisRequest.SourceImage image : request.images()) {
            VisualHazardContext result = visualHazardService.analyze(new VisualHazardAnalysisRequest(
                    request.userProvidedContext(), image.imageBase64(), request.executionContext()));
            if (model.isBlank()) model = result.model();
            for (VisualHazardContext.Candidate candidate : result.hazardCandidates()) {
                String key = candidate.hazardType().trim() + "\u0000" + candidate.description().trim();
                HostedVisualRun.HostedCandidate existing = deduplicated.get(key);
                if (existing != null) {
                    List<String> sources = new ArrayList<>(existing.sourceAttachmentIds());
                    if (!sources.contains(image.attachmentId())) sources.add(image.attachmentId());
                    deduplicated.put(key, new HostedVisualRun.HostedCandidate(existing.candidateId(), sources,
                            existing.hazardType(), existing.operationObject(), existing.description(),
                            existing.visibleEvidence(), existing.potentialRisk(), existing.judgement(),
                            Math.max(existing.confidence(), candidate.confidence()),
                            existing.needsManualVerification() || candidate.needsManualVerification()));
                    continue;
                }
                deduplicated.put(key, new HostedVisualRun.HostedCandidate(
                        runId + "-" + deduplicated.size(), List.of(image.attachmentId()), candidate.hazardType(),
                        candidate.operationObject(), candidate.description(), candidate.visibleEvidence(),
                        candidate.potentialRisk(), candidate.judgement(), candidate.confidence(),
                        candidate.needsManualVerification()));
            }
        }
        List<HostedVisualRun.HostedCandidate> candidates = new ArrayList<>(deduplicated.values());
        HostedVisualRun run = new HostedVisualRun(runId, runId, request.sourceRecordId(), request.sourceRecordVersion(),
                request.userProvidedContext(), model, candidates.isEmpty() ? "NO_CANDIDATE" : "CANDIDATES_READY",
                Instant.now(), candidates);
        visualRunRepository.save(run);
        return run;
    }

    public HostedAssessmentResponse assess(HostedHazardAssessmentRequest request) {
        requireContext(request.executionContext());
        HostedVisualRun run = visualRunRepository.find(request.visualRunId());
        if (run == null) throw new IllegalArgumentException("hosted 视觉研判不存在");
        Map<String, HostedVisualRun.HostedCandidate> source = new LinkedHashMap<>();
        run.candidates().forEach(candidate -> source.put(candidate.candidateId(), candidate));
        List<HostedAssessmentResponse.Item> results = new ArrayList<>();
        boolean knowledgeUsed = false;
        LegalAnswerTrace trace = null;
        VisualLegalQueryBuilder.VisualLegalQuery firstLegalQuery = null;
        for (HostedHazardAssessmentRequest.SelectedCandidate selected : request.candidates()) {
            HostedVisualRun.HostedCandidate candidate = source.get(selected.candidateId());
            if (candidate == null) throw new IllegalArgumentException("candidateId 不属于本次视觉研判");
            String description = nonBlank(selected.editedDescription(), candidate.description());
            String hazardType = nonBlank(selected.editedHazardType(), candidate.hazardType());
            VisualLegalQueryBuilder.VisualLegalQuery legalQuery = VisualLegalQueryBuilder.build(
                    hazardType, candidate.operationObject(), description, candidate.visibleEvidence(),
                    candidate.potentialRisk(), candidate.judgement(), candidate.confidence(),
                    candidate.needsManualVerification());
            if (firstLegalQuery == null) firstLegalQuery = legalQuery;
            HazardAssessmentResult raw = assessmentService.assess(legalQuery.query(), request.executionContext());
            if (trace == null) trace = raw.trace();
            HazardAssessment full = assessmentService.get(raw.assessmentId());
            List<LegalEvidence> evidence = raw.evidence() == null ? List.of() : raw.evidence();
            boolean hasEvidence = !evidence.isEmpty();
            knowledgeUsed |= hasEvidence;
            results.add(new HostedAssessmentResponse.Item(
                    candidate.candidateId(), candidate.sourceAttachmentIds(), hazardType,
                    description, candidate.judgement(), candidate.confidence(), candidate.visibleEvidence(), run.userProvidedContext(),
                    legalQuery.query(), legalQuery.confirmedFacts(),
                    hasEvidence ? risk(raw.riskLevel(), raw.riskExplanation()) : new HostedAssessmentResponse.RiskLevel("UNVERIFIED", null, "无相关 Evidence", true),
                    hasEvidence ? raw.suggestion() : List.of(), full == null ? List.of() : full.acceptanceCriteria(),
                    hasEvidence ? "HAZARD_LIKELY" : "NEED_MORE_INFORMATION", evidence));
        }
        String status = knowledgeUsed ? "ASSESSED" : "NO_RELEVANT_EVIDENCE";
        HostedAssessmentResponse response = new HostedAssessmentResponse(UUID.randomUUID().toString(), run.runId(), status, knowledgeUsed,
                trace(status, trace, firstLegalQuery), results);
        assessmentRepository.save(response);
        return response;
    }

    public HostedAssessmentResponse assessmentDetail(String assessmentId) {
        HostedAssessmentResponse result = assessmentRepository.find(assessmentId);
        if (result == null) throw new IllegalArgumentException("hosted 法规评估不存在");
        return result;
    }

    private HostedAssessmentResponse.RiskLevel risk(String value, String basis) {
        return new HostedAssessmentResponse.RiskLevel(normalizeRisk(value), null, basis, true);
    }

    private String normalizeRisk(String value) {
        if (value == null) return "UNVERIFIED";
        return switch (value.trim().toUpperCase()) {
            case "高", "HIGH" -> "HIGH";
            case "中", "MEDIUM" -> "MEDIUM";
            case "低", "LOW" -> "LOW";
            default -> "UNVERIFIED";
        };
    }

    private List<HostedAssessmentResponse.TraceStep> trace(String status, LegalAnswerTrace trace,
                                                            VisualLegalQueryBuilder.VisualLegalQuery legalQuery) {
        List<HostedAssessmentResponse.TraceStep> visual = legalQuery == null ? List.of()
                : List.of(new HostedAssessmentResponse.TraceStep("VISUAL_QUERY", legalQuery.query(), true),
                new HostedAssessmentResponse.TraceStep("VISUAL_FACTS", "确认事实数：" + legalQuery.confirmedFacts().size(), true));
        if (trace != null) {
            List<HostedAssessmentResponse.TraceStep> result = new ArrayList<>(visual);
            result.addAll(List.of(
                new HostedAssessmentResponse.TraceStep("REWRITE", trace.rewrittenQuestion(), !trace.originalQuestion().equals(trace.rewrittenQuestion())),
                new HostedAssessmentResponse.TraceStep("DECOMPOSE", "子问题数：" + trace.subQuestions().size(), trace.subQuestions().size() > 1),
                new HostedAssessmentResponse.TraceStep("INTENT", String.join(",", trace.matchedIntentIds()), !trace.matchedIntentIds().isEmpty()),
                new HostedAssessmentResponse.TraceStep("KNOWLEDGE_BASE", String.join(",", trace.searchedKnowledgeBases()), !trace.searchedKnowledgeBases().isEmpty()),
                new HostedAssessmentResponse.TraceStep("EVIDENCE_GATE", "候选=" + trace.retrievedCandidateCount() + "，证据=" + trace.evidenceCount(), trace.evidenceGatePassed()),
                new HostedAssessmentResponse.TraceStep("RESULT", status, true)));
            for (SubQuestionRetrievalTrace subQuestion : trace.retrievalTraces()) {
                for (com.safeguard.agent.rag.core.retrieval.RetrievalStageTrace stage : subQuestion.stages()) {
                    result.add(new HostedAssessmentResponse.TraceStep(
                            "RETRIEVAL_" + stage.stage(),
                            "子问题=" + subQuestion.question() + "，chunk数=" + stage.chunkIds().size(),
                            !stage.chunkIds().isEmpty()));
                }
            }
            return List.copyOf(result);
        }
        List<HostedAssessmentResponse.TraceStep> result = new ArrayList<>(visual);
        result.addAll(List.of(
                new HostedAssessmentResponse.TraceStep("NORMALIZE", "未改写：视觉候选默认作为原子隐患", false),
                new HostedAssessmentResponse.TraceStep("DECOMPOSE", "未拆解：由业务宿主逐候选调用", false),
                new HostedAssessmentResponse.TraceStep("RETRIEVE_EVIDENCE", "未返回详细检索轨迹", false),
                new HostedAssessmentResponse.TraceStep("RESULT", status, true)));
        return List.copyOf(result);
    }

    private void requireContext(com.safeguard.agent.framework.context.SafeGuardExecutionContext context) {
        if (context == null) throw new IllegalArgumentException("缺少可信 SafeGuard 执行上下文");
    }

    private String nonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }
}

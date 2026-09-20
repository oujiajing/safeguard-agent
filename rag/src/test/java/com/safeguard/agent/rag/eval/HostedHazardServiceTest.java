package com.safeguard.agent.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import com.safeguard.agent.rag.core.retrieval.RetrievalStageTrace;
import com.safeguard.agent.rag.dto.SubQuestionRetrievalTrace;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HostedHazardServiceTest {
    private final SafeGuardExecutionContext context = new SafeGuardExecutionContext(7L, 8L, 9L, 10L, "quick-shot:11", "trace-12");

    @Test void visualAnalysisKeepsCandidateAttachedToItsSourceImageAndPersistsRun() {
        VisualHazardService visual = Mockito.mock(VisualHazardService.class);
        HazardAssessmentService assessment = Mockito.mock(HazardAssessmentService.class);
        MemoryVisualRuns visualRuns = new MemoryVisualRuns();
        HostedHazardService service = new HostedHazardService(visual, assessment, visualRuns, new MemoryAssessments());
        VisualHazardContext.Candidate candidate = new VisualHazardContext.Candidate("old", "临边防护缺失", "楼梯口", "楼梯口未见连续防护栏杆", List.of("楼梯口边缘可见"), "坠落", "CONFIRMED", .88, true);
        when(visual.analyze(any())).thenReturn(new VisualHazardContext("analysis", "施工通道", "二层通道", "vlm", Instant.now(), List.of(candidate)));

        HostedVisualRun result = service.analyze(new HostedVisualAnalysisRequest("record-1", 5, "二层通道", List.of(new HostedVisualAnalysisRequest.SourceImage("attachment-9", "aGVsbG8=")), context));

        assertEquals("CANDIDATES_READY", result.status());
        assertEquals(List.of("attachment-9"), result.candidates().get(0).sourceAttachmentIds());
        assertEquals(result, visualRuns.find(result.runId()));
    }

    @Test void missingEvidenceIsConservativeAndDoesNotNeedConfirmation() {
        VisualHazardService visual = Mockito.mock(VisualHazardService.class);
        HazardAssessmentService assessment = Mockito.mock(HazardAssessmentService.class);
        MemoryVisualRuns visualRuns = new MemoryVisualRuns();
        MemoryAssessments assessments = new MemoryAssessments();
        HostedHazardService service = new HostedHazardService(visual, assessment, visualRuns, assessments);
        HostedVisualRun.HostedCandidate candidate = new HostedVisualRun.HostedCandidate("candidate-1", List.of("attachment-1"), "临边防护", "楼梯", "临边无防护", List.of("边缘可见"), "坠落", "SUSPECTED", .6, true);
        visualRuns.save(new HostedVisualRun("run-1", "run-1", "record-1", 2, "", "vlm", "CANDIDATES_READY", Instant.now(), List.of(candidate)));
        LegalAnswerTrace trace = new LegalAnswerTrace("临边无防护", "临边防护 楼梯", List.of("临边防护 楼梯"), List.of("intent-1"), List.of("kb-1"), 2, 0, false,
                List.of(new SubQuestionRetrievalTrace("临边防护 楼梯", List.of(
                        new RetrievalStageTrace("RAW_RECALL", List.of("chunk-1", "chunk-2")),
                        new RetrievalStageTrace("RERANK", List.of("chunk-1"))))));
        when(assessment.assess(anyString(), eq(context))).thenReturn(new HazardAssessmentResult("临边无防护", "临边防护", "高", "无相关证据", List.of(), List.of(), new HazardAssessmentResult.Action(false, false, null, "ASSESSMENT_ONLY"), "assessment-1", trace));
        when(assessment.get("assessment-1")).thenReturn(null);

        HostedAssessmentResponse result = service.assess(new HostedHazardAssessmentRequest("run-1", List.of(new HostedHazardAssessmentRequest.SelectedCandidate("candidate-1", null, null)), context));

        assertEquals("NO_RELEVANT_EVIDENCE", result.status());
        assertEquals("UNVERIFIED", result.assessments().get(0).riskLevel().value());
        assertEquals("NEED_MORE_INFORMATION", result.assessments().get(0).aiReviewSuggestion());
        assertTrue(result.assessments().get(0).legalRetrievalQuery().contains("隐患类型：临边防护"));
        assertTrue(result.assessments().get(0).legalRetrievalQuery().contains("可见事实：边缘可见"));
        assertTrue(result.workflowTrace().stream().anyMatch(step -> step.stage().equals("RETRIEVAL_RAW_RECALL") && step.message().contains("chunk数=2")));
        assertTrue(result.workflowTrace().stream().anyMatch(step -> step.stage().equals("RETRIEVAL_RERANK") && step.message().contains("chunk数=1")));
        verify(assessment).assess(anyString(), eq(context));
        assertEquals(result, assessments.find(result.assessmentId()));
    }

    @Test void assessmentRejectsCandidateOutsidePersistedVisualRun() {
        HostedHazardService service = new HostedHazardService(Mockito.mock(VisualHazardService.class), Mockito.mock(HazardAssessmentService.class), new MemoryVisualRuns(), new MemoryAssessments());
        assertThrows(IllegalArgumentException.class, () -> service.assess(new HostedHazardAssessmentRequest("missing", List.of(new HostedHazardAssessmentRequest.SelectedCandidate("candidate", null, null)), context)));
    }

    private static class MemoryVisualRuns implements HostedVisualRunRepository {
        private final java.util.Map<String, HostedVisualRun> values = new java.util.HashMap<>();
        @Override public void save(HostedVisualRun run) { values.put(run.runId(), run); }
        @Override public HostedVisualRun find(String runId) { return values.get(runId); }
    }

    private static class MemoryAssessments implements HostedAssessmentRepository {
        private final java.util.Map<String, HostedAssessmentResponse> values = new java.util.HashMap<>();
        @Override public void save(HostedAssessmentResponse assessment) { values.put(assessment.assessmentId(), assessment); }
        @Override public HostedAssessmentResponse find(String assessmentId) { return values.get(assessmentId); }
    }
}

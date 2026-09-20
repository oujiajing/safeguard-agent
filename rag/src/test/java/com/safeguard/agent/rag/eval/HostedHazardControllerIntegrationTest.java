package com.safeguard.agent.rag.eval;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.framework.config.SafeGuardServiceProperties;
import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Reproducible HTTP-level hosted loop; no Redis, model, vector store, or safe-flow write is needed. */
class HostedHazardControllerIntegrationTest {
    private static final String TOKEN = "test-service-token";
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        VisualHazardService visual = org.mockito.Mockito.mock(VisualHazardService.class);
        HazardAssessmentService assessment = org.mockito.Mockito.mock(HazardAssessmentService.class);
        VisualHazardContext.Candidate candidate = new VisualHazardContext.Candidate("candidate-1", "临边防护", "楼梯", "楼梯口无防护", List.of("边缘可见"), "坠落", "SUSPECTED", .6, true);
        when(visual.analyze(any())).thenReturn(new VisualHazardContext("analysis", "楼梯", "楼梯口", "test-vlm", Instant.now(), List.of(candidate)));
        when(assessment.assess(any(), any())).thenReturn(new HazardAssessmentResult("楼梯口无防护", "临边防护", "高", "test", List.of(), List.of(), new HazardAssessmentResult.Action(false, false, null, "ASSESSMENT_ONLY"), "assessment-1", new LegalAnswerTrace("楼梯口无防护", "楼梯口无防护", List.of("楼梯口无防护"), List.of(), List.of(), 0, 0, false, List.of())));
        HostedHazardService service = new HostedHazardService(visual, assessment, new MemoryVisualRuns(), new MemoryAssessments());
        SafeGuardServiceProperties properties = org.mockito.Mockito.mock(SafeGuardServiceProperties.class);
        when(properties.getToken()).thenReturn(TOKEN);
        mvc = MockMvcBuilders.standaloneSetup(new HostedHazardController(service, properties)).build();
    }

    @Test
    void hostedHttpLoopRejectsMissingTokenAndSupportsReadback() throws Exception {
        SafeGuardExecutionContext context = new SafeGuardExecutionContext(7L, 8L, 9L, 10L, "quick-shot:11", "trace-12");
        var requestNode = mapper.createObjectNode();
        requestNode.put("sourceRecordId", "record-1");
        requestNode.put("sourceRecordVersion", 1);
        requestNode.put("userProvidedContext", "楼梯口");
        var image = requestNode.putArray("images").addObject();
        image.put("attachmentId", "attachment-1");
        image.put("imageBase64", "aGVsbG8=");
        requestNode.set("executionContext", mapper.valueToTree(context));
        String request = mapper.writeValueAsString(requestNode);
        mvc.perform(post("/agent/v1/hosted/visual-analysis").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());

        MvcResult visual = mvc.perform(post("/agent/v1/hosted/visual-analysis").header("X-Safeguard-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANDIDATES_READY")).andReturn();
        JsonNode run = mapper.readTree(visual.getResponse().getContentAsString());
        String runId = run.get("runId").asText();
        String candidateId = run.get("candidates").get(0).get("candidateId").asText();
        var assessNode = mapper.createObjectNode();
        assessNode.put("visualRunId", runId);
        assessNode.putArray("candidates").addObject().put("candidateId", candidateId);
        assessNode.set("executionContext", mapper.valueToTree(context));
        String assessRequest = mapper.writeValueAsString(assessNode);
        MvcResult assessmentResult = mvc.perform(post("/agent/v1/hosted/hazard-assessments").header("X-Safeguard-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(assessRequest))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_RELEVANT_EVIDENCE")).andReturn();
        String assessmentId = mapper.readTree(assessmentResult.getResponse().getContentAsString()).get("assessmentId").asText();
        mvc.perform(get("/agent/v1/hosted/hazard-assessments/{assessmentId}", assessmentId).header("X-Safeguard-Service-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assessmentId").value(assessmentId));
    }

    private static final class MemoryVisualRuns implements HostedVisualRunRepository {
        private final Map<String, HostedVisualRun> values = new HashMap<>();
        @Override public void save(HostedVisualRun run) { values.put(run.runId(), run); }
        @Override public HostedVisualRun find(String runId) { return values.get(runId); }
    }

    private static final class MemoryAssessments implements HostedAssessmentRepository {
        private final Map<String, HostedAssessmentResponse> values = new HashMap<>();
        @Override public void save(HostedAssessmentResponse assessment) { values.put(assessment.assessmentId(), assessment); }
        @Override public HostedAssessmentResponse find(String assessmentId) { return values.get(assessmentId); }
    }
}

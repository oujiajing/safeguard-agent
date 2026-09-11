package com.safeguard.agent.rag.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.infra.chat.LLMService;
import com.safeguard.agent.legal.model.LegalEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class HazardAssessmentConfirmationTest {
    @Test void confirmationCreatesOnceAndRepeatedConfirmationIsIdempotent() {
        LegalAnswerService legal = mock(LegalAnswerService.class); LLMService llm = mock(LLMService.class);
        LegalEvidence e = new LegalEvidence("e1", "规范", "GB-1", "1", null, "CLAUSE", "应防护", "c", 1, .9F, .9F);
        when(legal.answer(any())).thenReturn(new LegalAnswerResponse("依据", List.of(e), List.of()));
        when(llm.chat(any())).thenReturn("{\"riskExplanation\":\"有风险\",\"suggestion\":[\"设置栏杆\"],\"acceptanceCriteria\":[\"牢固\"]}");
        HazardAssessmentRepository repo = new InMemoryHazardAssessmentRepository(); RectificationTaskCreator creator = mock(RectificationTaskCreator.class);
        when(creator.create(any(), any())).thenReturn(new RectificationTaskCreator.TaskCreationResult(true, "T1", "CREATED", null));
        HazardAssessmentService service = new HazardAssessmentService(legal, llm, new ObjectMapper(), repo, creator);
        HazardAssessmentResult result = service.assess("地下室临边无栏杆");
        assertThat(service.confirm(result.assessmentId()).status()).isEqualTo("TASK_CREATED");
        assertThat(service.confirm(result.assessmentId()).status()).isEqualTo("ALREADY_CREATED");
        verify(creator, times(1)).create(any(), any());
    }

    @Test void missingEvidenceCannotCreateTask() {
        LegalAnswerService legal = mock(LegalAnswerService.class); LLMService llm = mock(LLMService.class);
        when(legal.answer(any())).thenReturn(new LegalAnswerResponse(LegalAnswerService.NO_EVIDENCE, List.of(), List.of()));
        RectificationTaskCreator creator = mock(RectificationTaskCreator.class);
        HazardAssessmentService service = new HazardAssessmentService(legal, llm, new ObjectMapper(), new InMemoryHazardAssessmentRepository(), creator);
        HazardAssessmentResult result = service.assess("不明隐患");
        assertThat(service.confirm(result.assessmentId()).status()).isEqualTo("FAILED");
        verifyNoInteractions(creator);
    }
}

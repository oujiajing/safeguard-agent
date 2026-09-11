package com.safeguard.agent.rag.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.framework.convention.ChatRequest;
import com.safeguard.agent.infra.chat.LLMService;
import com.safeguard.agent.legal.model.LegalEvidence;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class HazardAssessmentServiceTest {
    @Mock private LegalAnswerService legalAnswerService;
    @Mock private LLMService llmService;

    private HazardAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new HazardAssessmentService(legalAnswerService, llmService, new ObjectMapper());
    }

    @Test
    void generatesGroundedAdviceAndOnlyReturnsConfirmationProposal() {
        LegalEvidence evidence = new LegalEvidence("evidence-1", "施工安全规范", "GB-TEST", "第十条",
                null, "CLAUSE", "临边应设置防护栏杆。", "chunk-1", 1, 0.9F, 0.8F);
        when(legalAnswerService.answer("地下室临边没有设置防护栏")).thenReturn(
                new LegalAnswerResponse("应设置防护栏[evidence-1]", List.of(evidence), List.of()));
        when(llmService.chat(any(ChatRequest.class))).thenReturn("""
                {"riskExplanation":"人员存在坠落风险。","suggestion":["设置连续防护栏杆"],"acceptanceCriteria":["栏杆牢固且覆盖临边"]}
                """);

        HazardAssessmentResult result = service.assess("地下室临边没有设置防护栏");

        assertThat(result.category()).isEqualTo("临边防护");
        assertThat(result.riskLevel()).isEqualTo("高");
        assertThat(result.evidence()).containsExactly(evidence);
        assertThat(result.suggestion()).containsExactly("设置连续防护栏杆", "验收标准：栏杆牢固且覆盖临边");
        assertThat(result.action().needCreateTask()).isTrue();
        assertThat(result.action().requiresConfirmation()).isTrue();
        assertThat(result.action().toolName()).isEqualTo("create_rectification_order");
        assertThat(result.action().status()).isEqualTo("CONFIRMATION_REQUIRED");
    }

    @Test
    void missingEvidenceDoesNotCallAdviceLlmAndStaysConservative() {
        when(legalAnswerService.answer("现场存在不明隐患")).thenReturn(
                new LegalAnswerResponse(LegalAnswerService.NO_EVIDENCE, List.of(), List.of()));

        HazardAssessmentResult result = service.assess("现场存在不明隐患");

        assertThat(result.riskLevel()).isEqualTo("待核实");
        assertThat(result.suggestion()).contains("补充现场照片、位置、作业类型和责任班组后再评估");
        verify(llmService, never()).chat(any(ChatRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"脚手架没有设置剪刀撑", "地下室临边没有设置防护栏", "施工现场配电箱没有防护",
            "高处作业未系安全带", "基坑支护变形", "吊装作业无指挥", "洞口未设置盖板", "临电线路破损",
            "脚手架连墙件缺失", "起重机械超载吊装"})
    void acceptsTenConstructionHazardCases(String hazard) {
        when(legalAnswerService.answer(hazard)).thenReturn(
                new LegalAnswerResponse("依据不足", List.of(), List.of()));

        HazardAssessmentResult result = service.assess(hazard);

        assertThat(result.hazard()).isEqualTo(hazard);
        assertThat(result.action().status()).isEqualTo("CONFIRMATION_REQUIRED");
    }
}

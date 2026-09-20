package com.safeguard.agent.rag.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.safeguard.agent.legal.model.LegalEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegalAnswerEvidenceSelectionTest {
    @Test
    void keepsHelmetEvidenceAndExcludesUnrelatedFireCitation() {
        LegalEvidence helmet = evidence("安全标准", "进入施工现场的人员必须正确佩戴安全帽。");
        LegalEvidence fire = evidence("消防规范", "疏散通道不应使用镜面反光材料。");

        assertThat(LegalAnswerService.selectDirectEvidence("人员疑似未佩戴安全帽", List.of(helmet, fire)))
                .containsExactly(helmet);
    }

    @Test
    void rejectsBroadCandidatesWhenQuestionHasNoSupportedSafetyAnchor() {
        LegalEvidence unrelated = evidence("施工安全规范", "施工现场应保持通道畅通并定期检查。 ");

        assertThat(LegalAnswerService.selectDirectEvidence("核电站反应堆检修作业的辐射剂量限值是多少", List.of(unrelated)))
                .isEmpty();
    }

    private LegalEvidence evidence(String title, String content) {
        return new LegalEvidence("e", title, null, "1", null, "NORMATIVE", content, "chunk", null, .8F, .8F);
    }
}

package com.safeguard.agent.legal.review;

import com.safeguard.agent.legal.enums.LegalContentRole;
import com.safeguard.agent.legal.enums.LegalStructureType;
import com.safeguard.agent.legal.model.LegalClause;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClauseSequenceGapDetectorTest {

    @Test
    void reportsOnlyInteriorGapWithinSameScope() {
        ClauseSequenceGapDetector detector = new ClauseSequenceGapDetector();
        List<LegalClause> clauses = List.of(
                clause("a", "4.1.1", "1", LegalContentRole.NORMATIVE),
                clause("b", "4.1.3", "1", LegalContentRole.NORMATIVE),
                clause("c", "4.2.1", "1", LegalContentRole.NORMATIVE),
                clause("d", "4.1.1", "1", LegalContentRole.COMMENTARY));

        assertThat(detector.detect(clauses)).singleElement().satisfies(signal -> {
            assertThat(signal.signalType()).isEqualTo(ReviewSignalType.CLAUSE_SEQUENCE_GAP);
            assertThat(signal.evidence()).containsEntry("expected", "4.1.2");
        });
    }

    @Test
    void doesNotTreatDuplicateOrReversedNumbersAsGap() {
        ClauseSequenceGapDetector detector = new ClauseSequenceGapDetector();
        assertThat(detector.detect(List.of(
                clause("a", "1.2", "1", LegalContentRole.NORMATIVE),
                clause("b", "1.2", "1", LegalContentRole.NORMATIVE),
                clause("c", "1.1", "1", LegalContentRole.NORMATIVE)))).isEmpty();
    }

    private static LegalClause clause(String id, String no, String chapter, LegalContentRole role) {
        return new LegalClause(id, "doc", role, LegalStructureType.CLAUSE,
                chapter, "", "", "", no, "", no, no, List.of(), "e1", "e2", 1, 1, 0, 1);
    }
}

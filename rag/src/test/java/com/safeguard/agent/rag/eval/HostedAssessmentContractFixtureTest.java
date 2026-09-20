package com.safeguard.agent.rag.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HostedAssessmentContractFixtureTest {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "assessmentId", "visualRunId", "status", "knowledgeUsed", "workflowTrace", "assessments");
    private static final Set<String> ITEM_FIELDS = Set.of(
            "candidateId", "sourceAttachmentIds", "hazardType", "hazardDescription", "visualJudgement",
            "visualConfidence", "visibleEvidence", "userProvidedContext", "legalRetrievalQuery", "legalQueryFacts",
            "riskLevel", "rectificationMeasures", "acceptanceCriteria", "aiReviewSuggestion", "legalEvidence");

    @Test
    void v1FixtureKeepsTheHostedAssessmentContractReadOnlyAndStable() throws Exception {
        JsonNode fixture = fixture();

        assertThat(fieldNames(fixture)).containsExactlyInAnyOrderElementsOf(ROOT_FIELDS);
        assertThat(fixture.path("assessmentId").asText()).isNotBlank();
        assertThat(fixture.path("visualRunId").asText()).isNotBlank();
        assertThat(fixture.path("status").asText()).isIn("ASSESSED", "NO_RELEVANT_EVIDENCE");
        assertThat(fixture.path("assessments")).isNotEmpty();
        assertThat(fieldNames(fixture.path("assessments").get(0))).containsExactlyInAnyOrderElementsOf(ITEM_FIELDS);
        assertThat(fieldNames(fixture)).noneMatch(this::isBusinessWriteField);
        assertThat(fieldNames(fixture.path("assessments").get(0))).noneMatch(this::isBusinessWriteField);
        assertThat(fixture.path("assessments").get(0).path("riskLevel").path("value").asText())
                .isIn("HIGH", "MEDIUM", "LOW", "UNVERIFIED");
    }

    private JsonNode fixture() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/hosted/hazard-assessment-v1.json")) {
            assertThat(input).as("contract fixture").isNotNull();
            return new ObjectMapper().readTree(input);
        }
    }

    private Set<String> fieldNames(JsonNode node) {
        java.util.Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private boolean isBusinessWriteField(String fieldName) {
        String normalized = fieldName.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("order") || normalized.contains("task") || normalized.contains("confirm")
                || normalized.contains("issue") || normalized.contains("write");
    }
}

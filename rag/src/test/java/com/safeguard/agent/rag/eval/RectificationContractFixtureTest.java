package com.safeguard.agent.rag.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RectificationContractFixtureTest {
    private static final Set<String> REVIEW_FIELDS = Set.of("reviewId", "orderId", "orderVersion", "inputDigest", "status", "criterionResults", "remainingRisks", "missingMaterials", "overallSuggestion", "confidence", "requiresManualReview", "workflowTrace");
    private static final Set<String> SUMMARY_FIELDS = Set.of("summaryId", "orderId", "orderVersion", "status", "sourceDigest", "sections", "citations", "generationTrace");

    @Test
    void reviewContractIsAdvisoryAndVersionBound() throws Exception {
        JsonNode node = read("/fixtures/hosted/rectification-review-v1.json");
        assertThat(fields(node)).containsExactlyInAnyOrderElementsOf(REVIEW_FIELDS);
        assertThat(node.path("status").asText()).isIn("REVIEWED", "NEED_MORE_INFORMATION", "STALE");
        assertThat(node.path("criterionResults").get(0).path("result").asText()).isIn("PASS", "FAIL", "UNCERTAIN", "NOT_OBSERVABLE");
        assertThat(fields(node)).noneMatch(this::businessWriteField);
    }

    @Test
    void summaryContractIsDraftOnlyAndCitable() throws Exception {
        JsonNode node = read("/fixtures/hosted/rectification-summary-v1.json");
        assertThat(fields(node)).containsExactlyInAnyOrderElementsOf(SUMMARY_FIELDS);
        assertThat(node.path("status").asText()).isIn("DRAFT", "CONFIRMED", "STALE");
        assertThat(node.path("sections")).isNotEmpty();
        assertThat(node.path("sections").get(0).path("sourceRefs")).isNotEmpty();
        assertThat(fields(node)).noneMatch(this::businessWriteField);
    }

    private JsonNode read(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new ObjectMapper().readTree(input);
        }
    }

    private Set<String> fields(JsonNode node) {
        java.util.Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private boolean businessWriteField(String name) {
        String value = name.toLowerCase(java.util.Locale.ROOT);
        return value.contains("confirm") || value.contains("issue") || value.contains("write") || value.equals("action");
    }
}

package com.safeguard.agent.rag.eval;

import java.util.List;

public record HostedRectificationReviewResponse(String reviewId, String orderId, int orderVersion, String status,
    String inputDigest, List<CriterionResult> criterionResults, List<String> remainingRisks,
    List<String> missingMaterials, String overallSuggestion, Double confidence, boolean requiresManualReview,
    List<TraceStep> workflowTrace) {
  public record CriterionResult(String criterion, String result, List<String> evidence, String reason) {}
  public record TraceStep(String stage, String message, boolean executed) {}
}

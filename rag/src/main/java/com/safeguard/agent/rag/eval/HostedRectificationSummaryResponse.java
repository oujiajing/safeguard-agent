package com.safeguard.agent.rag.eval;

import java.util.List;

public record HostedRectificationSummaryResponse(String summaryId, String orderId, int orderVersion, String status,
    String sourceDigest, List<Section> sections, List<String> citations, List<TraceStep> generationTrace) {
  public record Section(String key, String title, String content, List<String> sourceRefs) {}
  public record TraceStep(String stage, String message, boolean executed) {}
}

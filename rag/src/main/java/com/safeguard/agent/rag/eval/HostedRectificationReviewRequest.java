package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record HostedRectificationReviewRequest(@NotBlank String orderId, int orderVersion,
    @NotEmpty List<@Valid Criterion> criteria, List<String> beforeEvidence, List<String> afterEvidence,
    String rectificationDescription, @Valid SafeGuardExecutionContext executionContext,
    List<String> beforeImageData, List<String> afterImageData) {
  public HostedRectificationReviewRequest(String orderId, int orderVersion, List<Criterion> criteria,
      List<String> beforeEvidence, List<String> afterEvidence, String rectificationDescription,
      SafeGuardExecutionContext executionContext) {
    this(orderId, orderVersion, criteria, beforeEvidence, afterEvidence, rectificationDescription,
        executionContext, List.of(), List.of());
  }
  public record Criterion(@NotBlank String text) {}
}

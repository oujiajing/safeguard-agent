package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record HostedRectificationSummaryRequest(@NotBlank String orderId, int orderVersion,
    String hazardDescription, String rectificationDescription, String acceptanceResult, String acceptanceRemark,
    List<String> measures, List<String> acceptanceCriteria, List<String> flowFacts, List<String> citations,
    @Valid SafeGuardExecutionContext executionContext) {}

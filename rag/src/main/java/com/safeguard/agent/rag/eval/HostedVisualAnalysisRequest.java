package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.context.SafeGuardExecutionContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Trusted business-host input. Browser credentials must never reach this endpoint. */
public record HostedVisualAnalysisRequest(
        @NotBlank String sourceRecordId,
        int sourceRecordVersion,
        String userProvidedContext,
        @NotEmpty List<@Valid SourceImage> images,
        @Valid SafeGuardExecutionContext executionContext) {
    public record SourceImage(@NotBlank String attachmentId, @NotBlank String imageBase64) {}
}

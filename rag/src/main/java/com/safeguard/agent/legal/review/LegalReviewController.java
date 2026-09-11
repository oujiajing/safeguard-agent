package com.safeguard.agent.legal.review;

import com.safeguard.agent.framework.convention.Result;
import com.safeguard.agent.framework.web.Results;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/knowledge-base/docs")
public class LegalReviewController {

    private final LegalReviewService service;

    @GetMapping("/{doc-id}/legal-review/chunks/{chunk-id}/source-context")
    public Result<LegalChunkSourceContextVO> sourceContext(@PathVariable("doc-id") String docId,
                                                           @PathVariable("chunk-id") String chunkId) {
        return Results.success(service.sourceContext(docId, chunkId));
    }

    @GetMapping("/{doc-id}/legal-review/overview")
    public Result<LegalReviewOverviewVO> overview(@PathVariable("doc-id") String docId) {
        return Results.success(service.overview(docId));
    }

    @GetMapping("/{doc-id}/legal-review/signals")
    public Result<List<LegalReviewSignalVO>> list(@PathVariable("doc-id") String docId,
                                                  @RequestParam(required = false) String signalType,
                                                  @RequestParam(required = false) String reviewStatus) {
        return Results.success(service.list(docId, signalType, reviewStatus));
    }

    @PostMapping("/legal-review/{signal-id}/review")
    public Result<Void> review(@PathVariable("signal-id") String signalId,
                               @RequestBody @Validated ReviewRequest request) {
        service.review(signalId, request.signalStatus, request.reason, request.expectedVersion);
        return Results.success();
    }

    public record ReviewRequest(
            @NotNull LegalReviewStatus signalStatus,
            @NotBlank @Size(max = 1000) String reason,
            @NotNull Integer expectedVersion
    ) {
    }
}

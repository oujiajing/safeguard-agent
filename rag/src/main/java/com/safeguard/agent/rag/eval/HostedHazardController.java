package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.config.SafeGuardServiceProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Service-to-service hosted API; it intentionally has no confirmation/write endpoint. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/v1/hosted")
@ConditionalOnProperty(prefix = "safeguard.eval", name = "enabled", havingValue = "true")
public class HostedHazardController {
    private final HostedHazardService service;
    private final SafeGuardServiceProperties serviceProperties;

    @PostMapping("/visual-analysis")
    public HostedVisualRun analyze(@Valid @RequestBody HostedVisualAnalysisRequest request,
                                   HttpServletRequest httpRequest) {
        requireService(httpRequest);
        return service.analyze(request);
    }

    @PostMapping("/hazard-assessments")
    public HostedAssessmentResponse assess(@Valid @RequestBody HostedHazardAssessmentRequest request,
                                           HttpServletRequest httpRequest) {
        requireService(httpRequest);
        return service.assess(request);
    }

    @GetMapping("/hazard-assessments/{assessmentId}")
    public HostedAssessmentResponse assessmentDetail(@PathVariable String assessmentId,
                                                     HttpServletRequest httpRequest) {
        requireService(httpRequest);
        return service.assessmentDetail(assessmentId);
    }

    private void requireService(HttpServletRequest request) {
        String expected = serviceProperties.getToken();
        String actual = request.getHeader("X-Safeguard-Service-Token");
        if (expected == null || expected.isBlank() || actual == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Safe-team 服务身份无效");
        }
    }
}

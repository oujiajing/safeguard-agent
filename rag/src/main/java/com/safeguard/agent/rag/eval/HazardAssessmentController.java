package com.safeguard.agent.rag.eval;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import com.safeguard.agent.framework.config.SafeGuardServiceProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.http.HttpServletRequest;

/** Phase 3 SafeGuard demo endpoint; the context path contributes /api/safeguard-agent. */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "safeguard.eval", name = "enabled", havingValue = "true")
public class HazardAssessmentController {
    private final HazardAssessmentService service;
    private final SafeGuardServiceProperties serviceProperties;

    @PostMapping("/agent/hazard-assessment")
    public HazardAssessmentResult assess(@Valid @RequestBody HazardAssessmentRequest body,
                                         HttpServletRequest request) {
        requireService(request);
        return service.assess(body.hazardDescription(), body.executionContext());
    }

    @GetMapping("/agent/hazard-assessment/{assessmentId}")
    public HazardAssessment detail(@PathVariable String assessmentId, HttpServletRequest request) {
        requireService(request);
        return service.get(assessmentId);
    }

    @PostMapping("/agent/hazard-assessment/{assessmentId}/confirm")
    public HazardAssessmentService.ConfirmationResult confirm(
            @PathVariable String assessmentId,
            @RequestBody(required = false) HazardAssessmentConfirmRequest body,
            HttpServletRequest request) {
        requireService(request);
        if (body == null) return service.confirm(assessmentId);
        return service.confirm(assessmentId,
                new RectificationTaskCreator.TaskCreationContext(body.companyId(), body.departmentId(), body.teamId(),
                        body.idempotencyKey(), body.executionContext()));
    }

    @GetMapping("/agent/hazard-assessment")
    public HazardAssessmentResult assessByQuery(@RequestParam String hazardDescription,
                                                HttpServletRequest request) {
        requireService(request);
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "正式 SafeGuard Assessment 必须携带执行上下文");
    }

    private void requireService(HttpServletRequest request) {
        String expected = serviceProperties.getToken();
        String actual = request.getHeader("X-Safeguard-Service-Token");
        if (expected == null || expected.isBlank() || actual == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Safe-team 服务身份无效");
        }
    }
}

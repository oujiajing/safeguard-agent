package com.safeguard.agent.rag.eval;

import com.safeguard.agent.framework.config.SafeGuardServiceProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "safeguard.eval", name = "enabled", havingValue = "true")
public class VisualHazardController {
    private final VisualHazardService service;
    private final SafeGuardServiceProperties serviceProperties;

    @PostMapping("/agent/visual-hazard-analysis")
    public VisualHazardContext analyze(@Valid @RequestBody VisualHazardAnalysisRequest request,
                                       HttpServletRequest httpRequest) {
        requireService(httpRequest);
        return service.analyze(request);
    }

    @PostMapping("/agent/visual-hazard-analysis/{analysisId}/confirm")
    public VisualHazardService.VisualConfirmationResult confirm(
            @PathVariable String analysisId,
            @RequestBody VisualHazardConfirmationRequest request,
            HttpServletRequest httpRequest) {
        requireService(httpRequest);
        if (request == null || request.candidates() == null || request.candidates().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少确认一个视觉候选");
        }
        return service.confirm(analysisId, request, request.executionContext());
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

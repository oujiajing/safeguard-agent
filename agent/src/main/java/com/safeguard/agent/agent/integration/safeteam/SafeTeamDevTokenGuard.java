package com.safeguard.agent.agent.integration.safeteam;

import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SafeTeamDevTokenGuard {
    private final SafeTeamIntegrationProperties properties;
    private final Environment environment;

    public SafeTeamDevTokenGuard(SafeTeamIntegrationProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (properties.getDevToken() == null || properties.getDevToken().isBlank()) {
            return;
        }
        boolean nonProductionProfile = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(profile -> profile.equals("local") || profile.equals("dev") || profile.equals("test"));
        if (!nonProductionProfile) {
            throw new IllegalStateException("SAFE_TEAM_DEV_TOKEN 仅允许在 local/dev/test profile 使用");
        }
    }
}

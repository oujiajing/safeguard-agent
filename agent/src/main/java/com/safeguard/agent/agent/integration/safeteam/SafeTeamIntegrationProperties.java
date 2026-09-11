package com.safeguard.agent.agent.integration.safeteam;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "safeguard.safe-team")
public class SafeTeamIntegrationProperties {
    private boolean enabled;
    private String baseUrl = "http://localhost:8080";
    private String devToken;
    private String serviceToken;
    private long connectTimeoutMs = 2000;
    private long requestTimeoutMs = 8000;
    private int readMaxRetries = 1;
}

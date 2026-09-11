package com.safeguard.agent.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "safeguard.service")
public class SafeGuardServiceProperties {
    private String token;
}

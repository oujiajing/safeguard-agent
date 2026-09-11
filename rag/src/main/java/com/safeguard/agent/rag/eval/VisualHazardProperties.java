package com.safeguard.agent.rag.eval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "safeguard.vlm")
public class VisualHazardProperties {
    private String baseUrl = "http://localhost:11434";
    private String model = "qwen3-vl:8b-instruct-q4_K_M";
    private int maxImageBytes = 8 * 1024 * 1024;
    private int maxCandidates = 8;
}

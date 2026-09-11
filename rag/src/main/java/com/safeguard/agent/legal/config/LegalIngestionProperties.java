package com.safeguard.agent.legal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag.legal")
public class LegalIngestionProperties {

    private Chunk chunk = new Chunk();
    private Quality quality = new Quality();

    @Data
    public static class Chunk {
        private int maxTokens = 450;
        private int hardLimitTokens = 600;

        public void validate() {
            if (maxTokens <= 0) throw new IllegalArgumentException("rag.legal.chunk.max-tokens 必须 > 0");
            if (hardLimitTokens < maxTokens) {
                throw new IllegalArgumentException("rag.legal.chunk.hard-limit-tokens 不得小于 max-tokens");
            }
        }
    }

    @Data
    public static class Quality {
        private double maxUnstructuredRatio = 0.10;
        private double maxUnknownRoleRatio = 0.05;
    }
}

package com.safeguard.agent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 执行架构配置
 * 不挂在 rag 下：AGENT 档里 RAG 管线只是主 Agent 的一个 Tool，档位在语义上包含 RAG 而非从属于它
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "safeguard.engine")
public class OrchestrationProperties {

    /**
     * 档位取值，可选 workflow（默认）/ agent，大小写不敏感
     */
    private String type = "workflow";

    public OrchestrationMode getMode() {
        return OrchestrationMode.of(type);
    }
}

package com.safeguard.agent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.guidance")
public class GuidanceProperties {

    /**
     * 是否启用引导式问答
     */
    private Boolean enabled = true;

    /**
     * 歧义阈值
     * 歧义判定按候选意图路径重名触发，该值当前不参与判定，保留以兼容既有 yaml
     */
    private Double ambiguityScoreRatio = 0.8D;

    /**
     * 歧义阈值缓冲区宽度
     * 同 {@link #ambiguityScoreRatio}，当前不参与判定
     */
    private Double ambiguityMargin = 0.15D;

    /**
     * 单次最多展示的选项数量
     */
    private Integer maxOptions = 6;
}

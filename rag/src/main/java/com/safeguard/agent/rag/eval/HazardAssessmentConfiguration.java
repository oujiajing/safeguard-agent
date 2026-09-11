package com.safeguard.agent.rag.eval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HazardAssessmentConfiguration {
    @Bean
    @ConditionalOnMissingBean(RectificationTaskCreator.class)
    RectificationTaskCreator unavailableTaskCreator() {
        return (assessment, context) -> new RectificationTaskCreator.TaskCreationResult(false, null, null, "Safe-team 执行器未启用");
    }
}

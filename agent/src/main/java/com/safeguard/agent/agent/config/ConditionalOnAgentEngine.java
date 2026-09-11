package com.safeguard.agent.agent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Agent 执行架构条件开关：safeguard.engine.type=agent 时装配，终身制部署下 workflow 侧零开销
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(prefix = "safeguard.engine", name = "type", havingValue = "agent")
public @interface ConditionalOnAgentEngine {
}

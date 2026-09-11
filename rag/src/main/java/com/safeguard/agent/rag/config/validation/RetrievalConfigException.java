package com.safeguard.agent.rag.config.validation;

import lombok.Getter;

import java.util.List;

/**
 * 检索通道配置矛盾异常
 * <p>
 * 由 {@link RetrievalConfigEnvironmentPostProcessor} 在环境就绪阶段抛出，中断启动；
 * 携带全部违规交由 {@link RetrievalConfigFailureAnalyzer} 渲染成 APPLICATION FAILED TO START 诊断框
 * <p>
 * 有意为纯 {@link RuntimeException} 而非项目内 ServiceException：此为启动期配置错误，
 * 早于 Web 容器与全局异常处理器，不该走业务异常那套错误码 / HTTP 映射
 */
@Getter
public class RetrievalConfigException extends RuntimeException {

    private final transient List<RetrievalChannelConfigValidator.Violation> violations;

    public RetrievalConfigException(List<RetrievalChannelConfigValidator.Violation> violations) {
        super("检索通道配置存在矛盾（" + violations.size() + " 项）：后端未装配却启用了对应检索通道");
        this.violations = violations;
    }
}

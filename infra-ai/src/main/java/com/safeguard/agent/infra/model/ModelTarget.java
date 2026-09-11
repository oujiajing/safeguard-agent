package com.safeguard.agent.infra.model;

import com.safeguard.agent.infra.config.AIModelProperties;

/**
 * 模型目标配置记录
 * <p>
 * 用于封装 AI 模型的配置信息，包括模型标识、候选模型配置和提供商配置
 *
 * @param id        模型唯一标识符
 * @param candidate 模型候选配置，包含模型的具体参数和设置
 * @param provider  提供商配置，包含模型提供商的相关信息
 * @param timeoutMs 本次调用的超时预算（毫秒），来自命中的档位配置；null 表示不额外限制，走 HTTP 客户端默认
 */
public record ModelTarget(
        String id,
        AIModelProperties.ModelCandidate candidate,
        AIModelProperties.ProviderConfig provider,
        Long timeoutMs
) {
}

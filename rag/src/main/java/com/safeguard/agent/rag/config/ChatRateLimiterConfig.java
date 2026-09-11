package com.safeguard.agent.rag.config;

import com.safeguard.agent.rag.service.ratelimit.FairDistributedRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSE 聊天全局限流器 bean 装配
 */
@Configuration
public class ChatRateLimiterConfig {

    private static final String CHAT_LIMITER_NAME = "rag:global:chat";

    @Bean(initMethod = "start", destroyMethod = "stop")
    public FairDistributedRateLimiter chatRateLimiter(RedissonClient redissonClient,
                                                      RAGRateLimitProperties rateLimitProperties) {
        return new FairDistributedRateLimiter(
                CHAT_LIMITER_NAME,
                redissonClient,
                rateLimitProperties::getGlobalMaxConcurrent,
                rateLimitProperties::getGlobalLeaseSeconds,
                rateLimitProperties::getGlobalPollIntervalMs
        );
    }
}

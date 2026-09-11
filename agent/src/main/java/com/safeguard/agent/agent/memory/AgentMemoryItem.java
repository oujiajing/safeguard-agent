package com.safeguard.agent.agent.memory;

/**
 * 一条生效中的长期记忆，id 要带给 Judge 才有得指认
 */
public record AgentMemoryItem(String id, String content) {
}

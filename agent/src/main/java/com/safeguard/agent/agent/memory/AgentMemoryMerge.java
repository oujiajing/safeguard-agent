package com.safeguard.agent.agent.memory;

import java.util.List;

/**
 * 一组合并计划：ids 里的旧条目并成 content 这一条新条目
 */
public record AgentMemoryMerge(List<String> ids, String content) {
}

package com.safeguard.agent.core.parser.model;

import java.util.List;

/**
 * 列表 Block：由 ListChunker 处理，短列表 atomic、长列表按项分组
 */
public record ListBlock(
        Provenance provenance,
        boolean ordered,
        List<String> items
) implements Block {
}

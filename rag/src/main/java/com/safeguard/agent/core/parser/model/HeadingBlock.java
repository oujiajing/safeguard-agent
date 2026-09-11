package com.safeguard.agent.core.parser.model;

/**
 * 标题 Block：由 HeadingHandler 消费，自身不产 chunk，只累积进后续 chunk 的 outlinePath
 *
 * @param level markdown 标题级别，1-6
 */
public record HeadingBlock(
        Provenance provenance,
        int level,
        String text
) implements Block {
}

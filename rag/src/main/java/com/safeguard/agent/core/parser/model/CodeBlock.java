package com.safeguard.agent.core.parser.model;

/**
 * 代码块 Block：由 CodeChunker 产出 atomic chunk，代码切碎后不可用，故超预算也不切
 *
 * @param language 编程语言标识，可空
 */
public record CodeBlock(
        Provenance provenance,
        String language,
        String code
) implements Block {
}

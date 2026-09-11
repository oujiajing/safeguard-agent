package com.safeguard.agent.core.parser.model;

import java.util.List;
import java.util.Map;

/**
 * 解析器统一输出：有序 Block 列表 + 文档级元数据
 * <p>
 * 由 DocumentParser.parseStructured() 返回，作为解析阶段 → ChunkerNode 阶段之间的契约
 *
 * @param blocks   有序 Block 列表（章节、段落、表格、图片等按文档原始顺序）
 * @param metadata 文档级元数据，如来源、页数、解析器、耗时等
 */
public record ParsedDocument(List<Block> blocks, Map<String, Object> metadata) {

    public static ParsedDocument of(List<Block> blocks) {
        return new ParsedDocument(blocks != null ? blocks : List.of(), Map.of());
    }

    public static ParsedDocument of(List<Block> blocks, Map<String, Object> metadata) {
        return new ParsedDocument(blocks != null ? blocks : List.of(), metadata != null ? metadata : Map.of());
    }
}

package com.safeguard.agent.core.parser.model;

import java.util.List;

/**
 * 表格 Block：由 TableChunker 按 rowsPerChunk 切分，每个 chunk 都重复带上 headers
 * <p>
 * 到这里合并单元格已被 ExcelTableNormalizer 展开填充，多行表头已展平为单行、列名以竖线拼接如 "财务|收入"
 */
public record TableBlock(
        Provenance provenance,
        List<String> headers,
        List<List<String>> rows
) implements Block {
}

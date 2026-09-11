package com.safeguard.agent.core.parser.model;

/**
 * 原始 HTML 表格 Block：MinerU 的版面解析产出 {@code <table>} 而非管道表，由 HtmlTableChunker 按行切分
 * <p>
 * 刻意不拆成 {@link TableBlock} 的 headers/rows：合并单元格、单元格内的换行与公式片段在展开成二维表时
 * 必然失真，保留原 HTML 让展示端自行渲染
 *
 * @param html 完整表格 HTML，以 {@code <table} 开头
 */
public record HtmlTableBlock(
        Provenance provenance,
        String html
) implements Block {
}

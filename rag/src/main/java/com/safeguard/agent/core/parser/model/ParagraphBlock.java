package com.safeguard.agent.core.parser.model;

/**
 * 段落 Block：由 ParagraphChunker 按 token 切分，可跨段落合并到目标长度，但不跨 heading
 *
 * @param text 段落文本，保留链接、图片与行内代码标记而丢掉强调标记；markdown 里内嵌的非表格 HTML
 *             也原样落在此处（表格另走 {@link HtmlTableBlock}），故不能假定它是纯文本
 */
public record ParagraphBlock(
        Provenance provenance,
        String text
) implements Block {
}

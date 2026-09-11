package com.safeguard.agent.core.parser;

import com.safeguard.agent.core.parser.model.Block;
import com.safeguard.agent.core.parser.model.CodeBlock;
import com.safeguard.agent.core.parser.model.HeadingBlock;
import com.safeguard.agent.core.parser.model.HtmlTableBlock;
import com.safeguard.agent.core.parser.model.ImageBlock;
import com.safeguard.agent.core.parser.model.ListBlock;
import com.safeguard.agent.core.parser.model.ParagraphBlock;
import com.safeguard.agent.core.parser.model.TableBlock;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Block 列表 → 纯文本渲染器
 * <p>
 * 把 {@link com.safeguard.agent.core.parser.model.ParsedDocument} 的 Block 列表渲染为纯文本，
 * 供 PIPELINE 链路（ParserNode rawText 兼容）与 CHUNK 链路（分块输入）共用同一份实现
 * <p>
 * 简单实现：拼接各 Block 的可读文本表示。完整 markdown 渲染由 ChunkerNode 在 BlockAware 路径完成
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class BlockTextRenderer {

    /**
     * 把 Block 列表渲染为纯文本
     *
     * @param blocks 有序 Block 列表，为 null 时返回空串
     * @return 渲染后的纯文本
     */
    public static String render(List<Block> blocks) {
        if (blocks == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            if (b instanceof HeadingBlock h) {
                sb.append("#".repeat(Math.max(1, h.level())))
                        .append(' ').append(h.text() == null ? "" : h.text()).append("\n\n");
            } else if (b instanceof ParagraphBlock p) {
                sb.append(p.text() == null ? "" : p.text()).append("\n\n");
            } else if (b instanceof TableBlock t) {
                if (t.headers() != null) {
                    sb.append(String.join(" | ", t.headers())).append('\n');
                }
                if (t.rows() != null) {
                    for (List<String> row : t.rows()) {
                        sb.append(String.join(" | ", row)).append('\n');
                    }
                }
                sb.append('\n');
            } else if (b instanceof HtmlTableBlock t) {
                sb.append(t.html() == null ? "" : t.html()).append("\n\n");
            } else if (b instanceof ImageBlock i) {
                // 描述在前、图片 markdown 在后（与 ImageChunker 一致）：图生文描述是唯一可检索文本，
                // 整篇/legacy 等拍平路径若只渲染 ![](url) 会把描述丢掉，导致永远召回不到
                if (i.description() != null && !i.description().isBlank()) {
                    sb.append(i.description().strip()).append("\n\n");
                }
                sb.append("![")
                        .append(i.caption() == null ? "" : i.caption()).append("](")
                        .append(i.asset() == null ? "" : i.asset().publicUrl()).append(")\n\n");
            } else if (b instanceof CodeBlock c) {
                sb.append("```").append(c.language() == null ? "" : c.language())
                        .append('\n').append(c.code() == null ? "" : c.code()).append("\n```\n\n");
            } else if (b instanceof ListBlock l) {
                if (l.items() != null) {
                    for (int idx = 0; idx < l.items().size(); idx++) {
                        sb.append(l.ordered() ? (idx + 1) + ". " : "- ")
                                .append(l.items().get(idx)).append('\n');
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString().trim();
    }
}

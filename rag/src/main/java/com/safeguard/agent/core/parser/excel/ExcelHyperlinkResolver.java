package com.safeguard.agent.core.parser.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Hyperlink;

/**
 * Excel 超链接解析器
 * <p>
 * 解决"文字里放链接"的硬需求：cell 的可见文字与底层 URL 是分离的（URL 是 cell metadata），
 * 必须显式读出 hyperlink 并拼接成 markdown 内联形式 {@code [text](url)}
 * <p>
 * MinerU 等 OCR / 版面识别工具无法拿到此元数据 —— 这是 Excel 必须走 POI 的核心原因
 */
public final class ExcelHyperlinkResolver {

    private ExcelHyperlinkResolver() {
    }

    /**
     * 包装 cell 文字为 markdown 内联超链接形式
     *
     * @param cellText cell 的可见文字（已经过 ExcelValueFormatter 格式化）
     * @param cell     cell 实例，用于查询 hyperlink；可空
     * @return 如果 cell 有非空超链接：{@code [cellText](url)};否则原样返回 cellText
     */
    public static String wrap(String cellText, Cell cell) {
        if (cell == null) {
            return cellText == null ? "" : cellText;
        }
        Hyperlink hyperlink = cell.getHyperlink();
        if (hyperlink == null) {
            return cellText == null ? "" : cellText;
        }
        String url = hyperlink.getAddress();
        if (url == null || url.isBlank()) {
            return cellText == null ? "" : cellText;
        }
        String visible = (cellText == null || cellText.isEmpty()) ? hyperlink.getLabel() : cellText;
        if (visible == null || visible.isEmpty()) {
            visible = url;
        }
        return "[" + visible + "](" + url + ")";
    }
}

package com.safeguard.agent.core.parser.model;

/**
 * Block 来源信息，落进块元数据供排障时定位原始文档位置
 * <p>
 * sheetName 只作溯源标记、不参与向量文本：sheet 名由 Excel 解析器另产一个 HeadingBlock 走章节路径
 *
 * @param sourceFile 原始文件标识，文件 ID 或文件名
 * @param sheetName  Excel sheet 名，非 Excel 来源为 null
 */
public record Provenance(String sourceFile, String sheetName) {

    public static Provenance ofFile(String sourceFile) {
        return new Provenance(sourceFile, null);
    }

    public static Provenance ofExcelCell(String sourceFile, String sheetName) {
        return new Provenance(sourceFile, sheetName);
    }
}

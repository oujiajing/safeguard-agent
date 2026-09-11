package com.safeguard.agent.core.parser;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 文档解析后的文本规范化
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextCleanupUtil {

    /**
     * 依次剥 BOM、去行尾空格与制表符、连续三个以上空行压成两个、去首尾空白
     */
    public static String cleanup(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return text
                .replace("\uFEFF", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}

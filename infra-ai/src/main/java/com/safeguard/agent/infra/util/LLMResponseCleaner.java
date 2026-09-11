package com.safeguard.agent.infra.util;

import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * LLM 输出清理工具类
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class LLMResponseCleaner {

    private static final String FENCE = "```";

    private static final Pattern LEADING_CODE_FENCE = Pattern.compile("^```[\\w-]*\\s*\\n?");
    private static final Pattern TRAILING_CODE_FENCE = Pattern.compile("\\n?```\\s*$");

    /**
     * 移除 Markdown 代码块围栏（例如 ```json ... ```）
     * <p>
     * 有开头围栏截到最后一道收尾围栏为止，没有则只削末尾那道
     */
    public static String stripMarkdownCodeFence(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        if (!cleaned.startsWith(FENCE)) {
            return TRAILING_CODE_FENCE.matcher(cleaned).replaceFirst("").trim();
        }
        cleaned = LEADING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        int closing = cleaned.lastIndexOf(FENCE);
        return (closing < 0 ? cleaned : cleaned.substring(0, closing)).trim();
    }
}

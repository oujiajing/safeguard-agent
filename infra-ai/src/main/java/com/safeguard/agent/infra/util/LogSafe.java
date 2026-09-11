package com.safeguard.agent.infra.util;

import lombok.NoArgsConstructor;

/**
 * 日志安全工具类
 * <p>
 * 用于把可能较长、可能含用户/工具参数的原始响应截断后再落日志，避免日志膨胀与敏感信息完整外泄
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class LogSafe {

    /**
     * 默认预览长度
     */
    private static final int DEFAULT_MAX = 500;

    /**
     * 按默认长度截断
     */
    public static String preview(String raw) {
        return preview(raw, DEFAULT_MAX);
    }

    /**
     * 把原始文本截断到 max 字符，超出部分以省略号与总长度提示替代
     */
    public static String preview(String raw, int max) {
        if (raw == null) {
            return null;
        }
        if (raw.length() <= max) {
            return raw;
        }
        return raw.substring(0, max) + "...(truncated, total " + raw.length() + " chars)";
    }
}

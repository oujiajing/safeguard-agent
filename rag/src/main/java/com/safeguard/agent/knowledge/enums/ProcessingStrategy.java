package com.safeguard.agent.knowledge.enums;

import com.safeguard.agent.framework.exception.ClientException;

import java.util.Locale;

/** Domain processing route. This is intentionally separate from processMode and ingestionSpec. */
public enum ProcessingStrategy {
    GENERAL,
    LEGAL;

    public static ProcessingStrategy normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERAL;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ClientException("不支持的文档处理策略：" + raw);
        }
    }
}

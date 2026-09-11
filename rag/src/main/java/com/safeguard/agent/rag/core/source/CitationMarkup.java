package com.safeguard.agent.rag.core.source;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Pattern;

/**
 * 回答行内引用标记工具
 * <p>
 * 引用随回答正文持久化以保持位置稳定，但进入下一轮模型历史或推荐问题生成时应移除，
 * 避免上一轮的局部编号污染本轮引用编号
 */
public final class CitationMarkup {

    /**
     * 匹配系统定义的行内引用格式：
     * [1](#cite-1)、[10](#cite-10)
     * <p>
     * 不强制显示编号与锚点编号一致，因为清理逻辑需要兼容模型偶尔产生的错误格式，
     * 例如 [1](#cite-2) 也应从下一轮上下文中移除。
     */
    private static final Pattern INLINE_CITATION =
            Pattern.compile("\\[[1-9]\\d*]\\(#cite-[1-9]\\d*\\)");

    private CitationMarkup() {
    }

    public static String strip(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        return INLINE_CITATION.matcher(content).replaceAll("");
    }
}

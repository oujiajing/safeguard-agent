package com.safeguard.agent.infra.enums;

import lombok.Getter;

/**
 * 模型档位枚举
 * <p>
 * 档位表达「质量 / 成本 / 时延预算」，非业务任务本身。默认档为 standard，
 * 调用点想要更快/更强的模型时显式传入本枚举覆盖，路由层据此在对应档位内选候选并容错。
 * 每个枚举值的 key 对应 application.yaml 中 ai.chat.tiers 下的档位键
 */
@Getter
public enum Tier {

    /**
     * 快速档：低延迟优先，用于高频或低风险任务（标题、歧义、改写、摘要、入库富化/增强）
     */
    FAST("fast"),

    /**
     * 标准档：质量与成本平衡，未显式指定档位时的默认档
     */
    STANDARD("standard"),

    /**
     * 深度档：高质量、高成本，用于深度思考回答（通常由 thinking=true 触发）
     */
    DEEP("deep");

    /**
     * -- GETTER --
     *  对应 ai.chat.tiers 下的档位键
     */
    private final String key;

    Tier(String key) {
        this.key = key;
    }
}

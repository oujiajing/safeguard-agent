package com.safeguard.agent.agent.memory;

/**
 * 一次抽取的结局；mutated 表示记忆集整体有没有变（含合并/淘汰），与 applied 独立
 */
public record AgentMemoryOutcome(Status status, int applied, int pending, boolean mutated) {

    public enum Status {

        /**
         * yaml 长期记忆开关关闭
         */
        DISABLED,

        /**
         * 水位之后没有待处理的用户消息
         */
        NOTHING_PENDING,

        /**
         * 后台门槛没到，攒够再说；flush 不受它挡
         */
        BELOW_THRESHOLD,

        /**
         * 同会话已有在飞抽取
         */
        BUSY,

        /**
         * 仲裁调用或解析失败，这次抽取留待下次机会
         */
        FAILED,

        /**
         * 快照失配，本批作废
         */
        CONFLICT,

        /**
         * 容量拒收，只拒新增不删旧
         */
        CAPACITY_REJECTED,

        /**
         * 判完没落东西，水位照推
         */
        SETTLED_EMPTY,

        /**
         * 判完有落库
         */
        WRITTEN
    }

    static AgentMemoryOutcome of(Status status, int pending) {
        return new AgentMemoryOutcome(status, 0, pending, false);
    }

    /**
     * 压根没起跑，一次模型都没叫；后台每轮都会撞上这三种，不值得留 INFO
     */
    public boolean idle() {
        return status == Status.DISABLED
                || status == Status.NOTHING_PENDING
                || status == Status.BELOW_THRESHOLD;
    }
}

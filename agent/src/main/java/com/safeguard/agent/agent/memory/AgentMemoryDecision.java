package com.safeguard.agent.agent.memory;

/**
 * Judge 的一条决策，描述的是「处理完本批之后该长什么样」而不是逐句事件
 */
public record AgentMemoryDecision(Action action, String targetId, String content) {

    /**
     * 只列真会改库的动作；协议里的 NOOP 在 AgentMemoryJudge.toDecision 就解析成 null，到不了这里
     */
    public enum Action {

        /**
         * 新事实
         */
        ADD,

        /**
         * 新事实取代旧条目，两步在同一事务里
         */
        SUPERSEDE,

        /**
         * 用户明确要求忘掉某条目
         */
        RETRACT
    }

    public static AgentMemoryDecision add(String content) {
        return new AgentMemoryDecision(Action.ADD, null, content);
    }

    public static AgentMemoryDecision supersede(String targetId, String content) {
        return new AgentMemoryDecision(Action.SUPERSEDE, targetId, content);
    }

    public static AgentMemoryDecision retract(String targetId) {
        return new AgentMemoryDecision(Action.RETRACT, targetId, null);
    }

    /**
     * 会往记忆集里塞正文的两种动作，容量预演只看它们
     */
    public boolean introducesContent() {
        return action == Action.ADD || action == Action.SUPERSEDE;
    }
}

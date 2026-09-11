package com.safeguard.agent.core.parser.mineru;

/**
 * MinerU 任务状态
 * <p>
 * 与 MinerU SaaS 返回的 {@code state} 字段映射:
 * <ul>
 *   <li>{@code waiting-file / pending / running / converting} → {@link #RUNNING}</li>
 *   <li>{@code done} → {@link #DONE}</li>
 *   <li>{@code failed} → {@link #FAILED}</li>
 *   <li>无法识别的状态 → {@link #UNKNOWN}(视为 running 继续轮询)</li>
 * </ul>
 */
public enum MinerUTaskState {
    RUNNING,
    DONE,
    FAILED,
    UNKNOWN;

    /**
     * 从 MinerU 字段值映射到枚举
     */
    public static MinerUTaskState parse(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        return switch (raw.toLowerCase()) {
            case "done", "success", "succeeded", "completed" -> DONE;
            case "failed", "fail", "error" -> FAILED;
            case "waiting-file", "pending", "running", "converting", "queueing", "queue" -> RUNNING;
            default -> UNKNOWN;
        };
    }
}

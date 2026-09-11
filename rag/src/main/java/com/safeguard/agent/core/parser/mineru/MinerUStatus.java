package com.safeguard.agent.core.parser.mineru;

/**
 * MinerU 任务状态快照
 *
 * @param state        当前状态
 * @param zipUrl       结果 zip 下载 URL,仅 {@link MinerUTaskState#DONE} 时非空
 * @param errorMessage 失败原因,仅 {@link MinerUTaskState#FAILED} 时非空
 */
public record MinerUStatus(
        MinerUTaskState state,
        String zipUrl,
        String errorMessage
) {

    public boolean completed() {
        return state == MinerUTaskState.DONE;
    }

    public boolean failed() {
        return state == MinerUTaskState.FAILED;
    }
}

package com.safeguard.agent.rag.eval;

public interface HostedVisualRunRepository {
    void save(HostedVisualRun run);
    HostedVisualRun find(String runId);
}

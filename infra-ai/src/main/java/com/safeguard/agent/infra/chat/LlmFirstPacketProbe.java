package com.safeguard.agent.infra.chat;

import com.safeguard.agent.framework.trace.RagTraceNode;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 把 awaitFirstPacket 拆为独立 bean，便于 AOP 采集 TTFT trace
 * <p>
 * Spring AOP 不拦截类内 self-call，因此 RoutingLLMService 必须依赖外部 bean
 * 调用此方法，{@code @RagTraceNode} 才会生效
 */
@Component
public class LlmFirstPacketProbe {

    @RagTraceNode(name = "llm-first-packet", type = "LLM_TTFT")
    public ProbeStreamBridge.ProbeResult awaitFirstPacket(ProbeStreamBridge bridge,
                                                          long timeout,
                                                          TimeUnit unit) throws InterruptedException {
        return bridge.awaitFirstPacket(timeout, unit);
    }
}

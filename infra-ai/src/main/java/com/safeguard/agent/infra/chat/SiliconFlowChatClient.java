package com.safeguard.agent.infra.chat;

import com.safeguard.agent.framework.convention.ChatRequest;
import com.safeguard.agent.framework.trace.RagTraceNode;
import com.safeguard.agent.infra.enums.ModelProvider;
import com.safeguard.agent.infra.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SiliconFlowChatClient extends AbstractOpenAIStyleChatClient {

    @Override
    public String provider() {
        return ModelProvider.SILICON_FLOW.getId();
    }

    @Override
    protected boolean supportsEnableThinkingParam() {
        return true;
    }

    @Override
    @RagTraceNode(name = "siliconflow-chat", type = "LLM_PROVIDER")
    public String chat(ChatRequest request, ModelTarget target) {
        return doChat(request, target);
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        return doStreamChat(request, callback, target);
    }
}

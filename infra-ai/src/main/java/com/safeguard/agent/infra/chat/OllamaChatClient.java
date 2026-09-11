package com.safeguard.agent.infra.chat;

import com.google.gson.JsonObject;
import com.safeguard.agent.framework.convention.ChatRequest;
import com.safeguard.agent.framework.trace.RagTraceNode;
import com.safeguard.agent.infra.enums.ModelProvider;
import com.safeguard.agent.infra.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OllamaChatClient extends AbstractOpenAIStyleChatClient {

    @Override
    public String provider() {
        return ModelProvider.OLLAMA.getId();
    }

    @Override
    protected boolean requiresApiKey() {
        return false;
    }

    @Override
    protected void customizeRequestBody(JsonObject body, ChatRequest request) {
        body.addProperty("reasoning_effort", "none");
    }

    @Override
    @RagTraceNode(name = "ollama-chat", type = "LLM_PROVIDER")
    public String chat(ChatRequest request, ModelTarget target) {
        return doChat(request, target);
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        return doStreamChat(request, callback, target);
    }
}

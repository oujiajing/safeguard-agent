package com.safeguard.agent.infra.chat;

import com.google.gson.JsonObject;
import com.safeguard.agent.framework.convention.ChatMessage;
import com.safeguard.agent.framework.convention.ChatRequest;
import com.safeguard.agent.infra.config.AIModelProperties;
import com.safeguard.agent.infra.model.ModelTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线格式回归护栏：enable_thinking 是 DashScope 系私有扩展，只许发给声明认识它的提供商
 * <p>
 * 曾有一次改动让所有提供商无条件带上该字段，OpenAI 系网关判为 unknown_parameter 直接 400，
 * 且因该候选常在档位末位而击穿整条 fallback 链，这里按提供商逐个钉死请求体
 */
class ChatRequestThinkingParamTest {

    private static final String FIELD = "enable_thinking";

    @Test
    void bailianShouldCarryThinkingFlagWhenEnabled() {
        JsonObject body = buildBody(new BaiLianChatClient(), Boolean.TRUE);
        assertTrue(body.has(FIELD));
        assertTrue(body.get(FIELD).getAsBoolean());
    }

    /**
     * Qwen3 系不显式关会默认开启思考，故关闭时必须显式发 false 而非省略
     */
    @Test
    void bailianShouldExplicitlyDisableThinkingWhenClosed() {
        JsonObject body = buildBody(new BaiLianChatClient(), Boolean.FALSE);
        assertTrue(body.has(FIELD));
        assertFalse(body.get(FIELD).getAsBoolean());
    }

    @Test
    void bailianShouldTreatAbsentFlagAsDisabled() {
        JsonObject body = buildBody(new BaiLianChatClient(), null);
        assertTrue(body.has(FIELD));
        assertFalse(body.get(FIELD).getAsBoolean());
    }

    @Test
    void siliconFlowShouldCarryThinkingFlag() {
        assertTrue(buildBody(new SiliconFlowChatClient(), Boolean.TRUE).has(FIELD));
        assertTrue(buildBody(new SiliconFlowChatClient(), Boolean.FALSE).has(FIELD));
    }

    /**
     * 聚合网关走 OpenAI 原生协议，带上该字段会被判为未知参数
     */
    @Test
    void aiHubMixShouldNeverCarryThinkingFlag() {
        assertFalse(buildBody(new AIHubMixChatClient(), Boolean.TRUE).has(FIELD));
        assertFalse(buildBody(new AIHubMixChatClient(), Boolean.FALSE).has(FIELD));
        assertFalse(buildBody(new AIHubMixChatClient(), null).has(FIELD));
    }

    @Test
    void ollamaShouldDisableReasoningWithoutProviderSpecificFlags() {
        assertFalse(buildBody(new OllamaChatClient(), Boolean.TRUE).has(FIELD));
        assertFalse(buildBody(new OllamaChatClient(), Boolean.FALSE).has(FIELD));
        JsonObject body = buildBody(new OllamaChatClient(), Boolean.TRUE);
        assertFalse(body.has("think"));
        assertEquals("none", body.get("reasoning_effort").getAsString());
    }

    /**
     * 新增提供商默认不认识该字段，避免再次无条件外发
     */
    @Test
    void unknownProviderShouldOptOutByDefault() {
        AbstractOpenAIStyleChatClient client = new AbstractOpenAIStyleChatClient() {
            @Override
            public String provider() {
                return "brand-new-gateway";
            }

            @Override
            public String chat(ChatRequest request, ModelTarget target) {
                return doChat(request, target);
            }

            @Override
            public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
                return doStreamChat(request, callback, target);
            }
        };
        assertFalse(buildBody(client, Boolean.TRUE).has(FIELD));
    }

    /**
     * 流式与同步共用请求体构建，思考字段不应因 stream 标志而变化
     */
    @Test
    void streamBodyShouldFollowSameThinkingRule() {
        JsonObject bailian = new BaiLianChatClient().buildRequestBody(request(Boolean.TRUE), target(), true);
        assertTrue(bailian.has(FIELD));
        assertTrue(bailian.get("stream").getAsBoolean());
        assertFalse(new AIHubMixChatClient().buildRequestBody(request(Boolean.TRUE), target(), true).has(FIELD));
    }

    /**
     * 采样参数不受思考闸门影响，防止改动闸门时误伤公共字段
     */
    @Test
    void commonSamplingFieldsShouldStayIntact() {
        JsonObject body = buildBody(new AIHubMixChatClient(), Boolean.FALSE);
        assertEquals("test-model", body.get("model").getAsString());
        assertEquals(0D, body.get("temperature").getAsDouble());
        assertEquals(1, body.getAsJsonArray("messages").size());
    }

    private JsonObject buildBody(AbstractOpenAIStyleChatClient client, Boolean thinking) {
        return client.buildRequestBody(request(thinking), target(), false);
    }

    private ChatRequest request(Boolean thinking) {
        return ChatRequest.builder()
                .messages(List.of(ChatMessage.user("你好")))
                .temperature(0D)
                .thinking(thinking)
                .build();
    }

    private ModelTarget target() {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setModel("test-model");
        return new ModelTarget("test-id", candidate, new AIModelProperties.ProviderConfig(), null);
    }
}

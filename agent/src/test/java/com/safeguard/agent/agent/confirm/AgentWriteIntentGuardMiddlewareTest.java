package com.safeguard.agent.agent.confirm;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentWriteIntentGuardMiddlewareTest {

    @Test
    void recognizesNegativeWriteInstructions() {
        assertThat(AgentWriteIntentGuardMiddleware.hasExplicitNegativeWriteInstruction(
                List.of(new UserMessage("请仅评估现场隐患，不要创建整改工单")))).isTrue();
        assertThat(AgentWriteIntentGuardMiddleware.hasExplicitNegativeWriteInstruction(
                List.of(new UserMessage("请创建整改工单")))).isFalse();
        assertThat(AgentWriteIntentGuardMiddleware.hasExplicitNegativeWriteInstruction(
                List.of(new UserMessage("只评估即可，不需要下发整改")))).isTrue();
    }
}

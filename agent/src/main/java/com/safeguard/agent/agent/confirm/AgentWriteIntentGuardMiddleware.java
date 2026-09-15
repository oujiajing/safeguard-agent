package com.safeguard.agent.agent.confirm;

import com.safeguard.agent.agent.config.ConditionalOnAgentEngine;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** Hard model-side stop for explicit negative write instructions. Permission checks remain a second layer. */
@Component
@ConditionalOnAgentEngine
@RequiredArgsConstructor
public class AgentWriteIntentGuardMiddleware implements MiddlewareBase {

    public static final String WRITE_BLOCKED_ATTRIBUTE = "safeguard_write_blocked";
    private static final Set<String> WRITE_TOOLS = Set.of(
            "create_rectification_order", "issue_rectification", "create_rectification_from_assessment");

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext runtimeContext, ReasoningInput input,
                                        Function<ReasoningInput, Flux<AgentEvent>> next) {
        boolean blocked = hasExplicitNegativeWriteInstruction(input.messages());
        if (runtimeContext != null) runtimeContext.put(WRITE_BLOCKED_ATTRIBUTE, blocked);
        if (!blocked) return next.apply(input);
        List<ToolSchema> visible = input.tools().stream()
                .filter(tool -> !WRITE_TOOLS.contains(tool.getName()))
                .toList();
        return next.apply(new ReasoningInput(input.messages(), visible, input.options()));
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext runtimeContext, ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        if (input == null || input.toolCalls() == null) return next.apply(input);
        List<ToolUseBlock> safeCalls = input.toolCalls().stream()
                .filter(call -> !isUnsupportedWriteTool(call.getName()))
                .toList();
        return safeCalls.size() == input.toolCalls().size() ? next.apply(input) : next.apply(new ActingInput(safeCalls));
    }

    static boolean isUnsupportedWriteTool(String name) {
        if (name == null) return false;
        return Set.of("create_task", "create_work_order", "create_rectification_order", "issue_rectification").contains(name);
    }

    public static boolean isWriteBlocked(RuntimeContext context) {
        return context != null && Boolean.TRUE.equals(context.get(WRITE_BLOCKED_ATTRIBUTE));
    }

    public static boolean hasExplicitNegativeWriteInstruction(List<Msg> messages) {
        if (messages == null) return false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg message = messages.get(i);
            if (message.getRole() != MsgRole.USER) continue;
            String text = message.getContent().stream()
                    .filter(TextBlock.class::isInstance)
                    .map(TextBlock.class::cast)
                    .map(TextBlock::getText)
                    .reduce("", String::concat)
                    .toLowerCase(Locale.ROOT);
            return text.contains("不要创建") || text.contains("先不要创建") || text.contains("不创建")
                    || text.contains("不需要创建") || text.contains("无需创建")
                    || text.contains("不要生成") || text.contains("不生成")
                    || text.contains("不要下发") || text.contains("先不要下发") || text.contains("不下发")
                    || text.contains("不需要下发") || text.contains("无需下发")
                    || text.contains("仅评估") || text.contains("只评估") || text.contains("仅查询")
                    || text.contains("只查询") || text.contains("不要执行") || text.contains("不执行")
                    || text.contains("无需执行") || text.contains("不要提交") || text.contains("不提交")
                    || text.contains("不需要提交");
        }
        return false;
    }
}

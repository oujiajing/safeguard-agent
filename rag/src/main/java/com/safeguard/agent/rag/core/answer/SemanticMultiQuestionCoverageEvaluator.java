package com.safeguard.agent.rag.core.answer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.framework.convention.ChatMessage;
import com.safeguard.agent.framework.convention.ChatRequest;
import com.safeguard.agent.infra.chat.LLMService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Semantic completeness judge for a synthesized multi-question answer. */
@Service
@RequiredArgsConstructor
public class SemanticMultiQuestionCoverageEvaluator {
    private static final String SYSTEM_PROMPT = """
            你是回答完整性评测器。逐项判断回答是否给出了每个问题的实质性结论；同义改写、概括和不同措辞可以算覆盖，
            但只复述问题、只出现关键词、或回答了另一问题都不算。只输出 JSON：{"complete":true|false}。
            """;

    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public boolean isComplete(String answer, List<String> questions) {
        if (answer == null || answer.isBlank() || questions == null || questions.size() <= 1) return true;
        try {
            String numbered = java.util.stream.IntStream.range(0, questions.size())
                    .mapToObj(index -> (index + 1) + ". " + questions.get(index))
                    .collect(java.util.stream.Collectors.joining("\n"));
            String response = llmService.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.system(SYSTEM_PROMPT), ChatMessage.user(
                            "<questions>\n" + numbered + "\n</questions>\n<answer>\n" + answer + "\n</answer>")))
                    .temperature(0D).topP(1D).thinking(false).build());
            JsonNode parsed = objectMapper.readTree(response);
            return parsed.path("complete").isBoolean() && parsed.path("complete").asBoolean();
        } catch (Exception ignored) {
            // Do not turn a transient evaluator fault into a false success: retain the existing deterministic guard.
            return MultiQuestionCompletenessChecker.isComplete(answer, questions);
        }
    }
}

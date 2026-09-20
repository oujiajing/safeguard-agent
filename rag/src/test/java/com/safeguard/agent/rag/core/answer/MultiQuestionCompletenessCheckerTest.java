package com.safeguard.agent.rag.core.answer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiQuestionCompletenessCheckerTest {
    @Test
    void detectsOmittedSubQuestion() {
        assertFalse(MultiQuestionCompletenessChecker.isComplete("安全帽应正确佩戴。", List.of("安全帽佩戴要求", "临边防护要求")));
    }

    @Test
    void acceptsAllCoveredSubQuestions() {
        assertTrue(MultiQuestionCompletenessChecker.isComplete("1. 安全帽佩戴要求；2. 临边防护要求。", List.of("安全帽佩戴要求", "临边防护要求")));
    }
}

package com.safeguard.agent.rag.core.answer;

import java.util.List;
import java.util.Locale;

/** Lightweight lexical guard for multi-question synthesis; it is a retry trigger, not a semantic judge. */
public final class MultiQuestionCompletenessChecker {
    private MultiQuestionCompletenessChecker() {}

    public static boolean isComplete(String answer, List<String> questions) {
        if (answer == null || answer.isBlank() || questions == null || questions.size() <= 1) return true;
        String normalizedAnswer = answer.toLowerCase(Locale.ROOT);
        return questions.stream().allMatch(question -> covered(normalizedAnswer, question));
    }

    private static boolean covered(String answer, String question) {
        if (question == null || question.isBlank()) return true;
        String normalized = question.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
        if (normalized.length() < 2) return answer.contains(normalized);
        int required = Math.min(2, Math.max(1, normalized.length() / 4));
        int matched = 0;
        for (int i = 0; i + 1 < normalized.length(); i += 2) {
            if (answer.contains(normalized.substring(i, i + 2))) matched++;
        }
        return matched >= required;
    }
}

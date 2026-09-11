package com.safeguard.agent.legal.review;

import com.safeguard.agent.legal.model.LegalClause;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Detects observed interior gaps without sorting away source order or crossing scopes. */
public final class ClauseSequenceGapDetector {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)*");

    public List<ReviewSignalCandidate> detect(List<LegalClause> clauses) {
        Map<String, List<LegalClause>> groups = new LinkedHashMap<>();
        for (LegalClause clause : clauses == null ? List.<LegalClause>of() : clauses) {
            if (clause == null || clause.clauseNo() == null || clause.clauseNo().isBlank()) continue;
            String scope = String.join("|", value(clause.documentId()),
                    value(clause.contentRole() == null ? null : clause.contentRole().name()),
                    value(clause.chapterNo()), value(clause.sectionNo()));
            groups.computeIfAbsent(scope, ignored -> new ArrayList<>()).add(clause);
        }

        List<ReviewSignalCandidate> result = new ArrayList<>();
        for (List<LegalClause> group : groups.values()) {
            for (int i = 1; i < group.size(); i++) {
                LegalClause previous = group.get(i - 1);
                LegalClause actual = group.get(i);
                List<Integer> left = parse(previous.clauseNo());
                List<Integer> right = parse(actual.clauseNo());
                if (left == null || right == null || left.size() != right.size()
                        || !samePrefix(left, right) || right.get(right.size() - 1) <= left.get(left.size() - 1) + 1) {
                    continue;
                }
                String expected = number(left, left.get(left.size() - 1) + 1);
                String stableKey = String.join("|", actual.documentId(), "CLAUSE", previous.clauseId(), actual.clauseId(), expected);
                result.add(new ReviewSignalCandidate(stableKey, actual.documentId(), ReviewSignalScope.CLAUSE,
                        ReviewSignalType.CLAUSE_SEQUENCE_GAP, actual.clauseId(),
                        List.of(previous.clauseId(), actual.clauseId()), List.of(),
                        "观察到条款编号缺口：" + expected,
                        Map.of("previous", previous.clauseNo(), "expected", expected,
                                "actual", actual.clauseNo(), "missing", List.of(expected),
                                "previousClauseId", previous.clauseId(), "actualClauseId", actual.clauseId())));
            }
        }
        return result;
    }

    private static List<Integer> parse(String value) {
        if (!NUMBER.matcher(value.trim()).matches()) return null;
        String[] parts = value.trim().split("\\.");
        List<Integer> result = new ArrayList<>(parts.length);
        try {
            for (String part : parts) {
                int parsed = Integer.parseInt(part);
                if (parsed < 0) return null;
                result.add(parsed);
            }
            return result;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean samePrefix(List<Integer> left, List<Integer> right) {
        for (int i = 0; i < left.size() - 1; i++) if (!Objects.equals(left.get(i), right.get(i))) return false;
        return true;
    }

    private static String number(List<Integer> value, int last) {
        List<Integer> copy = new ArrayList<>(value);
        copy.set(copy.size() - 1, last);
        return copy.stream().map(String::valueOf).reduce((a, b) -> a + "." + b).orElse("");
    }

    private static String value(String value) { return value == null ? "" : value; }
}

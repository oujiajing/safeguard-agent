package com.safeguard.agent.legal.review;

import com.safeguard.agent.legal.model.LegalClause;
import com.safeguard.agent.legal.model.LegalSubUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects interior gaps in one clause's structured children or clearly delimited list lines. */
public final class EnumerationSequenceGapDetector {

    private static final Pattern MARKER = Pattern.compile("^\\s*(?:[（(]?)(\\d{1,3})(?:[）)]|[、:：]|\\.(?!\\d)|\\s+|(?=[\\u4e00-\\u9fff]))(.*)$");

    public List<ReviewSignalCandidate> detect(List<LegalClause> clauses) {
        List<ReviewSignalCandidate> result = new ArrayList<>();
        for (LegalClause clause : clauses == null ? List.<LegalClause>of() : clauses) {
            List<String> structured = structuredMarkers(clause.children());
            List<String> raw = lineMarkers(clause.rawText());
            List<String> markers = isContinuousFromOne(structured) ? structured : raw.size() > structured.size() ? raw : structured;
            if (markers.size() < 2) markers = raw;
            if (markers.size() < 2) continue;
            List<Integer> numbers = markers.stream().map(this::parse).filter(value -> value != null).toList();
            for (int i = 1; i < numbers.size(); i++) {
                int previous = numbers.get(i - 1);
                int actual = numbers.get(i);
                if (actual <= previous + 1) continue;
                String expected = String.valueOf(previous + 1);
                String stableKey = String.join("|", clause.documentId(), "ENUM", clause.clauseId(), expected);
                result.add(new ReviewSignalCandidate(stableKey, clause.documentId(), ReviewSignalScope.CLAUSE,
                        ReviewSignalType.ENUMERATION_SEQUENCE_GAP, clause.clauseId(), List.of(clause.clauseId()), List.of(),
                        "观察到条款内分点缺口：" + expected,
                        Map.of("listPosition", i, "sequence", numbers, "missing", List.of(expected),
                                "clauseId", clause.clauseId(), "excerpt", clause.rawText())));
                break;
            }
        }
        return result;
    }

    private List<String> structuredMarkers(List<LegalSubUnit> children) {
        if (children == null) return List.of();
        List<String> result = new ArrayList<>();
        boolean nestedList = false;
        for (int i = 0; i < children.size(); i++) {
            LegalSubUnit child = children.get(i);
            boolean outerItem = child.structureType() == com.safeguard.agent.legal.enums.LegalStructureType.ITEM;
            if (outerItem) nestedList = false;
            if (!outerItem && nestedList) continue;

            String marker = child.marker();
            if (marker == null || marker.isBlank()) {
                marker = bridgedMarker(children, i, result, child.rawText());
                if (marker == null) marker = markerFromText(child.rawText());
            }
            if (marker != null && !marker.isBlank()) result.add(marker);
            if (outerItem && child.rawText() != null && child.rawText().contains("下列")) nestedList = true;
        }
        return result;
    }

    private String bridgedMarker(List<LegalSubUnit> children, int index, List<String> accepted, String text) {
        if (accepted.isEmpty() || text == null) return null;
        Integer previous = parse(accepted.get(accepted.size() - 1));
        Integer next = nextExplicitMarker(children, index + 1);
        if (previous == null || next == null || next != previous + 2) return null;
        int expected = previous + 1;
        String stripped = text.stripLeading();
        String prefix = String.valueOf(expected);
        if (!stripped.startsWith(prefix) || stripped.length() == prefix.length()) return null;
        int suffixStart = prefix.length();
        if (stripped.charAt(suffixStart) == '.' && suffixStart + 1 < stripped.length()
                && Character.isDigit(stripped.charAt(suffixStart + 1))) return null;
        return prefix;
    }

    private Integer nextExplicitMarker(List<LegalSubUnit> children, int start) {
        for (int i = start; i < children.size(); i++) {
            LegalSubUnit child = children.get(i);
            if (child.structureType() != com.safeguard.agent.legal.enums.LegalStructureType.ITEM) continue;
            Integer marker = parse(child.marker());
            if (marker != null) return marker;
        }
        return null;
    }

    private String markerFromText(String text) {
        if (text == null) return null;
        Matcher matcher = MARKER.matcher(text);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private boolean isContinuousFromOne(List<String> markers) {
        if (markers.size() < 2) return false;
        for (int i = 0; i < markers.size(); i++) {
            Integer number = parse(markers.get(i));
            if (number == null || number != i + 1) return false;
        }
        return true;
    }

    private List<String> lineMarkers(String rawText) {
        if (rawText == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String line : rawText.split("\\R")) {
            Matcher matcher = MARKER.matcher(line);
            if (matcher.matches()) result.add(matcher.group(1));
        }
        return result;
    }

    private Integer parse(String value) {
        try {
            String digits = value.replaceAll("[^0-9]", "");
            return digits.isBlank() ? null : Integer.valueOf(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

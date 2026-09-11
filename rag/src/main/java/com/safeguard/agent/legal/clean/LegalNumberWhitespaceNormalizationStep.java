package com.safeguard.agent.legal.clean;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LegalNumberWhitespaceNormalizationStep implements LegalCleaningStep {

    private static final Pattern DECIMAL_PREFIX = Pattern.compile(
            "^(?<number>(?:[A-Za-z]\\s*[.]\\s*)?\\d+(?:\\s*[.]\\s*\\d+){1,4})(?<rest>.*)$");

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String normalize(String line) {
        if (line == null || line.isBlank()) return "";
        Matcher matcher = DECIMAL_PREFIX.matcher(line.strip());
        if (!matcher.matches()) return line.strip();
        String canonical = matcher.group("number").replaceAll("\\s+", "");
        return canonical + matcher.group("rest");
    }
}

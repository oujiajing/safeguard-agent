package com.safeguard.agent.legal.clean;

import org.springframework.stereotype.Component;

@Component
public class WhitespaceNormalizationStep implements LegalCleaningStep {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String normalize(String line) {
        if (line == null) return "";
        return line.replace('\t', ' ')
                .replaceAll("[\\p{Zs} ]+", " ")
                .strip();
    }
}

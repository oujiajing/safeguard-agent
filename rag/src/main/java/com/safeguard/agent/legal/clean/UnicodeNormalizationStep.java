package com.safeguard.agent.legal.clean;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

@Component
public class UnicodeNormalizationStep implements LegalCleaningStep {

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String normalize(String line) {
        if (line == null) return "";
        return Normalizer.normalize(line.replace("\uFEFF", ""), Normalizer.Form.NFKC);
    }
}

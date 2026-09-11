package com.safeguard.agent.legal.clean;

import org.springframework.stereotype.Component;

@Component
public class EmptyElementCleanupStep implements LegalCleaningStep {

    @Override
    public int order() {
        return 40;
    }

    @Override
    public String normalize(String line) {
        return line == null ? "" : line.strip();
    }
}

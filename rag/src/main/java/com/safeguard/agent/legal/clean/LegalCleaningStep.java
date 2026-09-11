package com.safeguard.agent.legal.clean;

public interface LegalCleaningStep {

    int order();

    String normalize(String line);
}

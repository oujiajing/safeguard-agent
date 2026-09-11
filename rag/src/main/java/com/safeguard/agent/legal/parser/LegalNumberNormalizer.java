package com.safeguard.agent.legal.parser;

public final class LegalNumberNormalizer {

    private LegalNumberNormalizer() {
    }

    public static String canonical(String number) {
        if (number == null) return null;
        return number.replaceAll("\\s+", "")
                .replace('．', '.')
                .replace('。', '.')
                .strip();
    }
}

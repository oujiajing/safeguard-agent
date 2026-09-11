package com.safeguard.agent.legal.model;

/** PDF source boundary retained through cleaning; never infer a legal number from its ordinal. */
public record LegalSourceBlock(String text, Kind kind, boolean body) {
    public enum Kind { HEADING, PARAGRAPH, TABLE, LIST, IMAGE, CODE }
}

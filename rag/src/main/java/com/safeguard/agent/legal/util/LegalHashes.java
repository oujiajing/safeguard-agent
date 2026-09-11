package com.safeguard.agent.legal.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class LegalHashes {

    private LegalHashes() {
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }

    public static String shortHash(String value) {
        String hash = sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return hash.substring(0, 20);
    }
}

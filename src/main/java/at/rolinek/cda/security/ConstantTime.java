package at.rolinek.cda.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Timing-safe comparison for secrets (admin token, clean-PDF password). */
public final class ConstantTime {

    private ConstantTime() {}

    /**
     * Constant-time string comparison that does not short-circuit on length difference.
     * Both inputs are SHA-256 hashed before comparing via MessageDigest.isEqual so the
     * fixed-length digests keep the length check inside isEqual from leaking the secret's
     * length. {@code null} is treated as the empty string.
     */
    public static boolean equals(String a, String b) {
        byte[] aHash = sha256((a != null ? a : "").getBytes(StandardCharsets.UTF_8));
        byte[] bHash = sha256((b != null ? b : "").getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(aHash, bHash);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java spec — this can never happen
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}

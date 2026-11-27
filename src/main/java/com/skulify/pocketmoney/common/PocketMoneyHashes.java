package com.skulify.pocketmoney.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Stable, non-reversible hashes for terminal identifiers and deterministic ids. */
public final class PocketMoneyHashes {

    private PocketMoneyHashes() {
    }

    /** Card UIDs and device keys are normalised before hashing so tap casing cannot vary. */
    public static String identifier(String value) {
        if (value == null || value.isBlank()) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.INVALID_AMOUNT, "An identifier is required");
        }
        return sha256(value.trim().toUpperCase());
    }

    public static UUID deterministicId(String scope, String value) {
        return UUID.nameUUIDFromBytes((scope + "|" + value).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                result.append(String.format("%02x", part));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

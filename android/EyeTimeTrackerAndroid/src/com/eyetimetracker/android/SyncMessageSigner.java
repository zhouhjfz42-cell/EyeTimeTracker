package com.eyetimetracker.android;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SyncMessageSigner {
    public static final long MAX_CLOCK_SKEW_SECONDS = 300L;

    private SyncMessageSigner() {
    }

    public static String sign(String type, long timestampUnixSeconds, String bodyJson, String sharedSecret) {
        if (sharedSecret == null || sharedSecret.isEmpty()) {
            throw new IllegalArgumentException("Shared secret is required.");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(canonicalPayload(type, timestampUnixSeconds, bodyJson).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value & 0xff));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign sync message.", ex);
        }
    }

    public static boolean verify(
            String type,
            long timestampUnixSeconds,
            String bodyJson,
            String sharedSecret,
            String signature,
            long nowUnixSeconds) {
        if (sharedSecret == null || sharedSecret.isEmpty() || signature == null || signature.isEmpty()) {
            return false;
        }
        if (Math.abs(nowUnixSeconds - timestampUnixSeconds) > MAX_CLOCK_SKEW_SECONDS) {
            return false;
        }

        String expected = sign(type, timestampUnixSeconds, bodyJson, sharedSecret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalPayload(String type, long timestampUnixSeconds, String bodyJson) {
        return safe(type) + "\n" + timestampUnixSeconds + "\n" + safe(bodyJson);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

package com.eyetimetracker.android;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;

public final class PairingCodeGenerator {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PairingCodeGenerator() {
    }

    public static String generate() {
        return generate(SECURE_RANDOM);
    }

    public static String generate(Random random) {
        int value = Math.max(0, random.nextInt(1_000_000));
        return String.format(Locale.US, "%06d", value);
    }
}

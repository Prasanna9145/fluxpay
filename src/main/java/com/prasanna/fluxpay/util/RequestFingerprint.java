package com.prasanna.fluxpay.util;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class RequestFingerprint {
    private RequestFingerprint() {}
    public static String payment(BigDecimal amount, String currency, String customerEmail) {
        String canonical = amount.stripTrailingZeros().toPlainString() + "|" + currency.toUpperCase(Locale.ROOT)
            + "|" + customerEmail.trim().toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
    }
}

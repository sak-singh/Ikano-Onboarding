package com.ikano.onboarding.service;

import java.util.Set;

/**
 * Masks sensitive answer values before they are persisted, logged, or
 * returned to the client on the review screen. Mirrors
 * {@code SENSITIVE_FIELD_NAMES} / {@code maskSensitiveValue} in the
 * original client-side {@code privateFlow.js} prototype.
 */
public final class SensitiveDataMasker {

    public static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "personalIdNumber",
            "idNumber",
            "pesel",
            "representativeId",
            "organisationNumber",
            "companyNif",
            "companyIdentifier",
            "iban"
    );

    private SensitiveDataMasker() {
    }

    public static boolean isSensitive(String fieldName) {
        return SENSITIVE_FIELD_NAMES.contains(fieldName);
    }

    public static Object mask(Object value) {
        if (value == null) return null;
        String str = String.valueOf(value);
        if (str.length() <= 4) return "*".repeat(str.length());
        return "*".repeat(str.length() - 4) + str.substring(str.length() - 4);
    }
}

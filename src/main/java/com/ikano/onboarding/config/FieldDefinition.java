package com.ikano.onboarding.config;

import java.util.List;

/** Definition of a single form field within a step. Mirrors the field objects in privateFlow.js. */
public record FieldDefinition(
        String name,
        String label,
        String type, // text, email, tel, number, select, radio, checkbox
        boolean required,
        String placeholder,
        String pattern,
        String patternHint,
        List<String> options,
        Double min
) {
    public static FieldDefinition text(String name, String label, boolean required) {
        return new FieldDefinition(name, label, "text", required, null, null, null, null, null);
    }
}


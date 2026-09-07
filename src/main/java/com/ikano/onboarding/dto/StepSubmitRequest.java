package com.ikano.onboarding.dto;

import java.util.Map;

/** Answers submitted for a single step. Values are plain JSON scalars (string/boolean/number). */
public record StepSubmitRequest(Map<String, Object> answers) {
}


package com.ikano.onboarding.service.integration;

import com.ikano.onboarding.domain.Outcome;

/** Result of a single mock integration call. */
public record IntegrationResult(Outcome outcome, String reason, String label) {
}


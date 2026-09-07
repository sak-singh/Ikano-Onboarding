package com.ikano.onboarding.dto;

import com.ikano.onboarding.domain.IntegrationType;
import com.ikano.onboarding.domain.Outcome;

import java.time.Instant;

public record IntegrationResultView(
        String stepKey,
        IntegrationType integrationType,
        Outcome outcome,
        String reason,
        String label,
        Instant timestamp
) {
}


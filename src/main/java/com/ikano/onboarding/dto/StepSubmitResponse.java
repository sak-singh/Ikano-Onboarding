package com.ikano.onboarding.dto;

import com.ikano.onboarding.domain.IntegrationType;
import com.ikano.onboarding.domain.Outcome;

/** Result of submitting one step, including the mock integration outcome (if the step triggers one). */
public record StepSubmitResponse(
        String stepKey,
        IntegrationType integrationType,
        Outcome outcome,
        String reason,
        String label,
        int nextStepIndex,
        boolean flowCompleted
) {
}


package com.ikano.onboarding.dto;

import com.ikano.onboarding.domain.SessionStatus;

import java.util.List;
import java.util.Map;

/** Review-screen payload: masked answers + all integration results so far. */
public record ReviewResponse(
        SessionStatus status,
        Map<String, Object> answers, // sensitive values pre-masked
        List<IntegrationResultView> integrationResults
) {
}


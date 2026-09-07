package com.ikano.onboarding.config;

import com.ikano.onboarding.domain.IntegrationType;

import java.util.List;

/** Definition of a single step within a country's flow. */
public record StepDefinition(
        String key,
        String title,
        String description,
        List<FieldDefinition> fields,
        IntegrationType integrationType // null if this step does not trigger an integration
) {
}


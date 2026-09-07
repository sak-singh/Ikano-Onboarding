package com.ikano.onboarding.dto;

import com.ikano.onboarding.domain.Country;
import com.ikano.onboarding.domain.CustomerType;
import jakarta.validation.constraints.NotNull;

public record CreateSessionRequest(
        @NotNull Country country,
        @NotNull CustomerType customerType
) {
}


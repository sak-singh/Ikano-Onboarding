package com.ikano.onboarding.service.integration;

import com.ikano.onboarding.domain.Outcome;
import org.springframework.stereotype.Service;

/**
 * Mock PEP/sanctions declaration service, ported from
 * {@code MockIntegrations.pepSanctions(...)}.
 */
@Service
public class PepSanctionsMockService {

    public IntegrationResult check(boolean selfDeclaredPep) {
        if (selfDeclaredPep) {
            return new IntegrationResult(Outcome.MANUAL_REVIEW, "possible_hit", "PEP declaration flagged for manual review");
        }
        return new IntegrationResult(Outcome.PASS, "no_hit", "No PEP/sanctions hit");
    }
}


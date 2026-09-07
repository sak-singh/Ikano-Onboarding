package com.ikano.onboarding.service.integration;

import com.ikano.onboarding.domain.Country;
import com.ikano.onboarding.domain.Outcome;
import org.springframework.stereotype.Service;

/**
 * Mock company-registry / KYB service (Bolagsverket / Registro Mercantil /
 * CEIDG-KRS style). Deterministic: the registry identifier's last
 * character decides the outcome. Examples of outputs per the brief:
 * active_company, dissolved, unknown_representative, missing_ubo.
 */
@Service
public class RegistryMockService {

    public IntegrationResult check(Country country, String registryId) {
        String value = registryId == null ? "" : registryId.trim().toUpperCase();

        if (value.isBlank()) {
            return new IntegrationResult(Outcome.FAIL, "missing_identifier", "Registry identifier is required");
        }

        char last = value.charAt(value.length() - 1);

        if (last == '9') {
            return new IntegrationResult(Outcome.FAIL, "dissolved", "Company registry lookup: company not active");
        }
        if (last == '0') {
            return new IntegrationResult(Outcome.MANUAL_REVIEW, "unknown_representative", "Registry lookup needs manual review (representative unclear)");
        }
        return new IntegrationResult(Outcome.PASS, "active_company", "Company registry lookup: active company confirmed");
    }
}


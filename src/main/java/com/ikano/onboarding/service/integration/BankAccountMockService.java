package com.ikano.onboarding.service.integration;

import com.ikano.onboarding.domain.Outcome;
import org.springframework.stereotype.Service;

/**
 * Mock bank-account verification service (IBAN check). Deterministic
 * outcomes per the brief's example outputs: iban_verified, name_mismatch,
 * unreachable. The "unreachable" case degrades gracefully to manual
 * review rather than throwing, per the production-mindset requirement to
 * handle all integration outcomes explicitly (simulated timeout/retry).
 */
@Service
public class BankAccountMockService {

    public IntegrationResult check(String iban) {
        String value = iban == null ? "" : iban.trim().toUpperCase().replace(" ", "");

        if (value.isBlank() || value.length() < 15) {
            return new IntegrationResult(Outcome.FAIL, "invalid_iban", "IBAN format not recognised");
        }

        char last = value.charAt(value.length() - 1);

        if (last == '9') {
            return new IntegrationResult(Outcome.MANUAL_REVIEW, "unreachable", "Bank account service unreachable - routed to manual review");
        }
        if (last == '0') {
            return new IntegrationResult(Outcome.MANUAL_REVIEW, "name_mismatch", "Account holder name does not match - manual review");
        }
        return new IntegrationResult(Outcome.PASS, "iban_verified", "IBAN verified");
    }
}


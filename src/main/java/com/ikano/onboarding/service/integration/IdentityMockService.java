package com.ikano.onboarding.service.integration;

import com.ikano.onboarding.domain.Country;
import com.ikano.onboarding.domain.Outcome;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Mock identity/KYC service. Deterministic per-country rules, ported 1:1
 * from {@code MockIntegrations.identity(...)} in the client-side
 * {@code privateFlow.js} prototype, so behaviour is consistent between the
 * original UI-only prototype and this backend.
 * <p>
 * Real national eID/BankID/Clave/PESEL services are never called - this is
 * a deliberately simple, typed client boundary per country.
 */
@Service
public class IdentityMockService {

    private static final Pattern SWEDEN_PATTERN = Pattern.compile("^[0-9]{8}-?[0-9]{4}$");
    private static final Pattern SPAIN_DNI_PATTERN = Pattern.compile("^[0-9]{8}[A-Z]$");
    private static final Pattern SPAIN_NIE_PATTERN = Pattern.compile("^[XYZ][0-9]{7}[A-Z]$");
    private static final Pattern POLAND_PESEL_PATTERN = Pattern.compile("^[0-9]{11}$");

    public IntegrationResult check(Country country, String rawIdValue) {
        String value = rawIdValue == null ? "" : rawIdValue.trim().toUpperCase();

        return switch (country) {
            case SWEDEN -> checkSweden(value);
            case SPAIN -> checkSpain(value);
            case POLAND -> checkPoland(value);
        };
    }

    private IntegrationResult checkSweden(String value) {
        if (!SWEDEN_PATTERN.matcher(value).matches()) {
            return new IntegrationResult(Outcome.FAIL, "invalid_format", "Invalid personal identity number format");
        }
        String lastDigit = value.replace("-", "").substring(value.replace("-", "").length() - 1);
        if ("0".equals(lastDigit)) {
            return new IntegrationResult(Outcome.MANUAL_REVIEW, "manual_review", "BankID could not auto-confirm - manual review");
        }
        return new IntegrationResult(Outcome.PASS, "verified", "BankID identity verified");
    }

    private IntegrationResult checkSpain(String value) {
        boolean dniValid = SPAIN_DNI_PATTERN.matcher(value).matches();
        boolean nieValid = SPAIN_NIE_PATTERN.matcher(value).matches();
        if (!dniValid && !nieValid) {
            return new IntegrationResult(Outcome.FAIL, "document_mismatch", "DNI/NIE format not recognised");
        }
        String letter = value.substring(value.length() - 1);
        if ("M".equals(letter)) {
            return new IntegrationResult(Outcome.MANUAL_REVIEW, "manual_review", "Document verification needs manual review");
        }
        return new IntegrationResult(Outcome.PASS, "verified", "Clave/DNIe identity verified");
    }

    private IntegrationResult checkPoland(String value) {
        if (!POLAND_PESEL_PATTERN.matcher(value).matches()) {
            return new IntegrationResult(Outcome.FAIL, "invalid_format", "PESEL must be 11 digits");
        }
        String lastDigit = value.substring(value.length() - 1);
        if ("0".equals(lastDigit)) {
            return new IntegrationResult(Outcome.MANUAL_REVIEW, "manual_review", "eID verification needs manual review");
        }
        return new IntegrationResult(Outcome.PASS, "verified", "eID / Trusted Profile identity verified");
    }
}


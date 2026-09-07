package com.ikano.onboarding.service.integration;

import com.ikano.onboarding.domain.Outcome;
import org.springframework.stereotype.Service;

/**
 * Mock credit-bureau + affordability service, ported from
 * {@code MockIntegrations.creditBureau(...)}. Deterministic: computes
 * disposable income from declared monthly income/expenses and maps it to
 * an outcome.
 */
@Service
public class CreditBureauMockService {

    public IntegrationResult check(Double monthlyIncome, Double monthlyExpenses) {
        if (monthlyIncome == null || monthlyIncome <= 0) {
            return new IntegrationResult(Outcome.FAIL, "invalid_income", "Income figure not usable for scoring");
        }

        double expenses = monthlyExpenses == null ? 0 : monthlyExpenses;
        double disposable = monthlyIncome - expenses;

        if (disposable < 0) {
            return new IntegrationResult(Outcome.MANUAL_REVIEW, "debt_flags", "Negative disposable income - manual review");
        }
        if (disposable < 300) {
            return new IntegrationResult(Outcome.MANUAL_REVIEW, "low_disposable_income", "Low disposable income - manual review");
        }
        return new IntegrationResult(Outcome.PASS, "decision_reason:affordable", "Affordability check passed");
    }
}


package com.ikano.onboarding.service.integration;

import com.ikano.onboarding.domain.Outcome;
import org.springframework.stereotype.Service;

/** Deterministic business credit/risk mock based on turnover and costs. */
@Service
public class BusinessCreditMockService {

    public IntegrationResult check(Double annualTurnover, Double monthlyCosts) {
        if (annualTurnover == null || annualTurnover <= 0) {
            return new IntegrationResult(Outcome.FAIL, "invalid_turnover", "Turnover figure not usable for scoring");
        }

        double costs = monthlyCosts == null ? 0 : monthlyCosts;
        double riskRatio = costs == 0 ? 0 : (annualTurnover / 12.0) / costs;

        if (riskRatio < 1.0) {
            return new IntegrationResult(Outcome.MANUAL_REVIEW, "debt_flags", "Operating ratio indicates risk - manual review");
        }
        if (riskRatio < 1.5) {
            return new IntegrationResult(Outcome.MANUAL_REVIEW, "low_disposable_income", "Borderline affordability - manual review");
        }
        return new IntegrationResult(Outcome.PASS, "decision_reason:affordable", "Business affordability check passed");
    }
}

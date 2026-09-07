package com.ikano.onboarding.config;

import com.ikano.onboarding.domain.Country;
import com.ikano.onboarding.domain.IntegrationType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side flow configuration for private-individual onboarding,
 * ported 1:1 from {@code PRIVATE_FLOWS} in the client-side
 * {@code privateFlow.js} prototype. This is the single source of truth
 * going forward - the client will eventually fetch this via
 * {@code GET /api/private/{country}/flow} instead of hardcoding it.
 * <p>
 * Adding a new country/step means adding an entry here - no branching
 * logic in the controller/service layer.
 */
@Component
public class PrivateFlowConfig {

    private final Map<Country, FlowDefinition> flows = new EnumMap<>(Country.class);

    public PrivateFlowConfig() {
        flows.put(Country.SWEDEN, buildSweden());
        flows.put(Country.SPAIN, buildSpain());
        flows.put(Country.POLAND, buildPoland());
    }

    public FlowDefinition get(Country country) {
        FlowDefinition flow = flows.get(country);
        if (flow == null) {
            throw new IllegalArgumentException("No private flow configured for country: " + country);
        }
        return flow;
    }

    private FlowDefinition buildSweden() {
        return new FlowDefinition("Sweden \u2013 Private individual", List.of(
                new StepDefinition("identity", "Identity verification",
                        "We use a BankID-style mock check to confirm your identity.",
                        List.of(
                                FieldDefinition.text("fullName", "Full legal name", true),
                                new FieldDefinition("personalIdNumber", "Personal identity number", "text", true,
                                        "YYYYMMDD-XXXX", null, null, null, null)
                        ),
                        IntegrationType.IDENTITY),
                new StepDefinition("contact", "Contact details & address",
                        "Confirm how we can reach you and where you live.",
                        List.of(
                                new FieldDefinition("email", "Email address", "email", true, null, null, null, null, null),
                                new FieldDefinition("phone", "Mobile number", "tel", true, null, null, null, null, null),
                                FieldDefinition.text("street", "Street address", true),
                                new FieldDefinition("postalCode", "Postal code", "text", true, null,
                                        "^[0-9]{3}\\s?[0-9]{2}$", "e.g. 111 22", null, null),
                                FieldDefinition.text("city", "City", true)
                        ),
                        null),
                new StepDefinition("consent", "Consent & declarations",
                        "Legally required declarations before we can proceed.",
                        List.of(
                                new FieldDefinition("dataConsent", "I consent to processing of my personal data for onboarding purposes",
                                        "checkbox", true, null, null, null, null, null),
                                new FieldDefinition("isPep", "Are you, or a close family member, a Politically Exposed Person (PEP)?",
                                        "radio", true, null, null, null, List.of("no", "yes"), null),
                                new FieldDefinition("taxResidency", "Tax residency country", "select", true, null, null, null,
                                        List.of("Sweden", "Other EU", "Outside EU"), null)
                        ),
                        IntegrationType.PEP_SANCTIONS),
                new StepDefinition("affordability-input", "Employment, income & household",
                        "Used to run the affordability check in the next step.",
                        List.of(
                                new FieldDefinition("employmentStatus", "Employment status", "select", true, null, null, null,
                                        List.of("Employed", "Self-employed", "Student", "Unemployed", "Retired"), null),
                                new FieldDefinition("monthlyIncome", "Monthly net income (SEK)", "number", true, null, null, null, null, 0.0),
                                new FieldDefinition("monthlyExpenses", "Monthly housing & living costs (SEK)", "number", true, null, null, null, null, 0.0),
                                new FieldDefinition("householdSize", "Household size", "number", true, null, null, null, null, 1.0)
                        ),
                        null),
                new StepDefinition("credit-bureau", "Credit bureau & affordability decision",
                        "We run a deterministic mock credit-bureau and affordability check using the figures you provided.",
                        List.of(),
                        IntegrationType.CREDIT_BUREAU),
                new StepDefinition("review", "Review & submit", "Please review your details before submitting.", List.of(), null)
        ));
    }

    private FlowDefinition buildSpain() {
        return new FlowDefinition("Spain \u2013 Private individual", List.of(
                new StepDefinition("identity", "Identity verification",
                        "We use a Clave/DNIe-style mock document check to confirm your identity.",
                        List.of(
                                FieldDefinition.text("fullName", "Full legal name", true),
                                new FieldDefinition("idNumber", "DNI or NIE", "text", true,
                                        "e.g. 12345678A or X1234567L", null, null, null, null)
                        ),
                        IntegrationType.IDENTITY),
                new StepDefinition("contact", "Contact details, province & address",
                        "Confirm how we can reach you and your tax residency.",
                        List.of(
                                new FieldDefinition("email", "Email address", "email", true, null, null, null, null, null),
                                new FieldDefinition("phone", "Mobile number", "tel", true, null, null, null, null, null),
                                FieldDefinition.text("province", "Province", true),
                                FieldDefinition.text("street", "Address", true),
                                new FieldDefinition("taxResidency", "Tax residency country", "select", true, null, null, null,
                                        List.of("Spain", "Other EU", "Outside EU"), null)
                        ),
                        null),
                new StepDefinition("consent", "Consent & declarations",
                        "Legally required declarations before we can proceed.",
                        List.of(
                                new FieldDefinition("dataConsent", "I consent to processing of my personal data for onboarding purposes",
                                        "checkbox", true, null, null, null, null, null),
                                new FieldDefinition("isPep", "Are you, or a close family member, a Politically Exposed Person (PEP)?",
                                        "radio", true, null, null, null, List.of("no", "yes"), null)
                        ),
                        IntegrationType.PEP_SANCTIONS),
                new StepDefinition("affordability-input", "Employment, income & dependants",
                        "Used to run the affordability check in the next step.",
                        List.of(
                                new FieldDefinition("employmentStatus", "Employment status", "select", true, null, null, null,
                                        List.of("Employed", "Self-employed", "Student", "Unemployed", "Retired"), null),
                                new FieldDefinition("monthlyIncome", "Monthly net income (EUR)", "number", true, null, null, null, null, 0.0),
                                new FieldDefinition("monthlyExpenses", "Monthly housing costs (EUR)", "number", true, null, null, null, null, 0.0),
                                new FieldDefinition("dependants", "Number of dependants", "number", true, null, null, null, null, 0.0)
                        ),
                        null),
                new StepDefinition("credit-bureau", "Credit bureau & affordability decision",
                        "We run a deterministic mock credit-bureau and affordability check using the figures you provided.",
                        List.of(),
                        IntegrationType.CREDIT_BUREAU),
                new StepDefinition("review", "Review & submit", "Please review your details before submitting.", List.of(), null)
        ));
    }

    private FlowDefinition buildPoland() {
        return new FlowDefinition("Poland \u2013 Private individual", List.of(
                new StepDefinition("identity", "Identity verification",
                        "We use an eID/Trusted Profile-style mock check to confirm your identity.",
                        List.of(
                                FieldDefinition.text("fullName", "Full legal name", true),
                                new FieldDefinition("pesel", "PESEL", "text", true, "11 digits", null, null, null, null)
                        ),
                        IntegrationType.IDENTITY),
                new StepDefinition("contact", "Contact details & registered address",
                        "Confirm how we can reach you and where you're registered.",
                        List.of(
                                new FieldDefinition("email", "Email address", "email", true, null, null, null, null, null),
                                new FieldDefinition("phone", "Mobile number", "tel", true, null, null, null, null, null),
                                FieldDefinition.text("street", "Registered address", true),
                                FieldDefinition.text("city", "City", true)
                        ),
                        null),
                new StepDefinition("consent", "Consent & declarations",
                        "Legally required declarations before we can proceed.",
                        List.of(
                                new FieldDefinition("dataConsent", "I consent to processing of my personal data for onboarding purposes",
                                        "checkbox", true, null, null, null, null, null),
                                new FieldDefinition("isPep", "Are you, or a close family member, a Politically Exposed Person (PEP)?",
                                        "radio", true, null, null, null, List.of("no", "yes"), null)
                        ),
                        IntegrationType.PEP_SANCTIONS),
                new StepDefinition("affordability-input", "Employment, income & affordability",
                        "Used to run the BIK-style credit-bureau check in the next step.",
                        List.of(
                                new FieldDefinition("employmentStatus", "Employment status", "select", true, null, null, null,
                                        List.of("Employed", "Self-employed", "Student", "Unemployed", "Retired"), null),
                                new FieldDefinition("monthlyIncome", "Monthly net income (PLN)", "number", true, null, null, null, null, 0.0),
                                new FieldDefinition("monthlyExpenses", "Monthly living costs (PLN)", "number", true, null, null, null, null, 0.0)
                        ),
                        null),
                new StepDefinition("credit-bureau", "BIK-style credit bureau & decision",
                        "We run a deterministic mock credit-bureau and affordability check using the figures you provided.",
                        List.of(),
                        IntegrationType.CREDIT_BUREAU),
                new StepDefinition("review", "Review & submit", "Please review your details before submitting.", List.of(), null)
        ));
    }
}


package com.ikano.onboarding.config;

import com.ikano.onboarding.domain.Country;
import com.ikano.onboarding.domain.IntegrationType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Config-driven business onboarding flows for Sweden, Spain and Poland. */
@Component
public class BusinessFlowConfig {

    private final Map<Country, FlowDefinition> flows = new EnumMap<>(Country.class);

    public BusinessFlowConfig() {
        flows.put(Country.SWEDEN, buildSweden());
        flows.put(Country.SPAIN, buildSpain());
        flows.put(Country.POLAND, buildPoland());
    }

    public FlowDefinition get(Country country) {
        FlowDefinition flow = flows.get(country);
        if (flow == null) {
            throw new IllegalArgumentException("No business flow configured for country: " + country);
        }
        return flow;
    }

    private FlowDefinition buildSweden() {
        return new FlowDefinition("Sweden - Business", List.of(
                new StepDefinition("company", "Company details", "Collect organisation number, legal name and legal form.", List.of(
                        new FieldDefinition("organisationNumber", "Organisation number", "text", true, null, null, null, null, null),
                        new FieldDefinition("legalName", "Legal name", "text", true, null, null, null, null, null),
                        new FieldDefinition("legalForm", "Legal form", "select", true, null, null, null, List.of("AB", "HB", "Enskild firma"), null)
                ), null),
                new StepDefinition("registry", "Registry lookup", "Run Bolagsverket-style company registry lookup.", List.of(), IntegrationType.REGISTRY),
                new StepDefinition("representative", "Representative & signatory", "Confirm authorized representative and signatory rights.", List.of(
                        new FieldDefinition("representativeName", "Representative name", "text", true, null, null, null, null, null),
                        new FieldDefinition("representativeId", "Representative personal id", "text", true, null, null, null, null, null),
                        new FieldDefinition("signatoryRights", "Signatory rights confirmed", "checkbox", true, null, null, null, null, null)
                ), IntegrationType.IDENTITY),
                new StepDefinition("ubo", "Beneficial owners", "Collect beneficial owners and sanctions declaration.", List.of(
                        new FieldDefinition("uboCount", "Number of UBOs", "number", true, null, null, null, null, 1.0),
                        new FieldDefinition("uboPep", "Any UBO is PEP?", "radio", true, null, null, null, List.of("no", "yes"), null)
                ), IntegrationType.PEP_SANCTIONS),
                new StepDefinition("risk-input", "Business activity & turnover", "Capture activity, turnover, purpose and expected usage.", List.of(
                        new FieldDefinition("businessActivity", "Business activity", "text", true, null, null, null, null, null),
                        new FieldDefinition("annualTurnover", "Annual turnover (SEK)", "number", true, null, null, null, null, 0.0),
                        new FieldDefinition("monthlyCosts", "Monthly costs (SEK)", "number", true, null, null, null, null, 0.0)
                ), null),
                new StepDefinition("business-credit", "Business credit decision", "Run business credit/risk mock.", List.of(), IntegrationType.CREDIT_BUREAU),
                new StepDefinition("review", "Review & submit", "Review summary, accept terms and submit.", List.of(), null)
        ));
    }

    private FlowDefinition buildSpain() {
        return new FlowDefinition("Spain - Business", List.of(
                new StepDefinition("company", "Company details", "Collect company NIF, legal form and registered address.", List.of(
                        new FieldDefinition("companyNif", "Company NIF", "text", true, null, null, null, null, null),
                        new FieldDefinition("legalForm", "Legal form", "select", true, null, null, null, List.of("SL", "SA", "Autonomo"), null),
                        new FieldDefinition("registeredAddress", "Registered address", "text", true, null, null, null, null, null)
                ), null),
                new StepDefinition("registry", "Registry lookup", "Run Registro Mercantil / tax-status-style lookup.", List.of(), IntegrationType.REGISTRY),
                new StepDefinition("representative", "Representative verification", "Verify legal representative using DNI/NIE identity mock.", List.of(
                        new FieldDefinition("representativeName", "Representative name", "text", true, null, null, null, null, null),
                        new FieldDefinition("representativeId", "Representative DNI/NIE", "text", true, null, null, null, null, null)
                ), IntegrationType.IDENTITY),
                new StepDefinition("ubo", "UBO and sanctions", "Collect beneficial owners and sanctions declaration.", List.of(
                        new FieldDefinition("ownershipPercent", "Largest ownership %", "number", true, null, null, null, null, 0.0),
                        new FieldDefinition("uboPep", "Any UBO is PEP?", "radio", true, null, null, null, List.of("no", "yes"), null)
                ), IntegrationType.PEP_SANCTIONS),
                new StepDefinition("risk-input", "Sector and turnover", "Capture sector, turnover and expected usage.", List.of(
                        new FieldDefinition("sector", "Sector", "text", true, null, null, null, null, null),
                        new FieldDefinition("annualTurnover", "Annual turnover (EUR)", "number", true, null, null, null, null, 0.0),
                        new FieldDefinition("monthlyCosts", "Monthly costs (EUR)", "number", true, null, null, null, null, 0.0),
                        new FieldDefinition("iban", "IBAN", "text", true, null, null, null, null, null)
                ), null),
                new StepDefinition("business-credit", "Business credit decision", "Run business credit/risk mock.", List.of(), IntegrationType.CREDIT_BUREAU),
                new StepDefinition("bank-account", "Bank account verification", "Run IBAN verification mock.", List.of(), IntegrationType.BANK_ACCOUNT),
                new StepDefinition("review", "Review & submit", "Review summary, accept terms and submit.", List.of(), null)
        ));
    }

    private FlowDefinition buildPoland() {
        return new FlowDefinition("Poland - Business", List.of(
                new StepDefinition("company", "Company details", "Collect NIP, REGON/KRS/CEIDG and legal form.", List.of(
                        new FieldDefinition("companyIdentifier", "NIP / REGON / KRS", "text", true, null, null, null, null, null),
                        new FieldDefinition("legalForm", "Legal form", "select", true, null, null, null, List.of("Sp. z o.o.", "S.A.", "Jednoosobowa dzialalnosc"), null)
                ), null),
                new StepDefinition("registry", "Registry lookup", "Run CEIDG/KRS-style lookup.", List.of(), IntegrationType.REGISTRY),
                new StepDefinition("representative", "Representative authority", "Confirm board member / sole proprietor authority.", List.of(
                        new FieldDefinition("representativeName", "Representative name", "text", true, null, null, null, null, null),
                        new FieldDefinition("representativeId", "Representative PESEL", "text", true, null, null, null, null, null)
                ), IntegrationType.IDENTITY),
                new StepDefinition("ubo", "UBO and sanctions", "Collect UBO details and sanctions declaration.", List.of(
                        new FieldDefinition("uboCount", "Number of UBOs", "number", true, null, null, null, null, 1.0),
                        new FieldDefinition("uboPep", "Any UBO is PEP?", "radio", true, null, null, null, List.of("no", "yes"), null)
                ), IntegrationType.PEP_SANCTIONS),
                new StepDefinition("risk-input", "VAT/tax and activity", "Capture VAT/tax status, activity and expected usage.", List.of(
                        new FieldDefinition("vatStatus", "VAT status", "select", true, null, null, null, List.of("Active", "Inactive"), null),
                        new FieldDefinition("annualTurnover", "Annual turnover (PLN)", "number", true, null, null, null, null, 0.0),
                        new FieldDefinition("monthlyCosts", "Monthly costs (PLN)", "number", true, null, null, null, null, 0.0),
                        new FieldDefinition("iban", "IBAN", "text", true, null, null, null, null, null)
                ), null),
                new StepDefinition("business-credit", "Business credit decision", "Run business credit/risk mock.", List.of(), IntegrationType.CREDIT_BUREAU),
                new StepDefinition("bank-account", "Bank account verification", "Run bank account verification mock.", List.of(), IntegrationType.BANK_ACCOUNT),
                new StepDefinition("review", "Review & submit", "Review summary, accept terms and submit.", List.of(), null)
        ));
    }
}


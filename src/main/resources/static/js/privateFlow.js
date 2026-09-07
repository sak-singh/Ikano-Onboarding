/**
 * Private-individual onboarding flow configuration & mock engine.
 * Mirrors the "Minimum flow matrix - Private individual onboarding"
 * table on page 5 of the requirement brief.
 *
 * Each country entry is pure configuration (fields, validation, which
 * mock integration to call). The step *renderer* in onboarding.js is
 * generic and has no per-country branching - adding a 4th market only
 * means adding an entry to PRIVATE_FLOWS below.
 */

/* ---------------------------------------------------------------------
 * Deterministic mock integrations
 * ------------------------------------------------------------------- */

const MockIntegrations = {
    /** Identity/KYC mock - format + deterministic outcome per country. */
    identity(country, idValue) {
        const value = (idValue || "").trim().toUpperCase();

        if (country === "SWEDEN") {
            const valid = /^[0-9]{8}-?[0-9]{4}$/.test(value);
            if (!valid) return { outcome: "FAIL", reason: "invalid_format", label: "Invalid personal identity number format" };
            const lastDigit = value.replace("-", "").slice(-1);
            if (lastDigit === "0") return { outcome: "MANUAL_REVIEW", reason: "manual_review", label: "BankID could not auto-confirm - manual review" };
            return { outcome: "PASS", reason: "verified", label: "BankID identity verified" };
        }

        if (country === "SPAIN") {
            const dniValid = /^[0-9]{8}[A-Z]$/.test(value);
            const nieValid = /^[XYZ][0-9]{7}[A-Z]$/.test(value);
            if (!dniValid && !nieValid) return { outcome: "FAIL", reason: "document_mismatch", label: "DNI/NIE format not recognised" };
            const letter = value.slice(-1);
            if (letter === "M") return { outcome: "MANUAL_REVIEW", reason: "manual_review", label: "Document verification needs manual review" };
            return { outcome: "PASS", reason: "verified", label: "Clave/DNIe identity verified" };
        }

        if (country === "POLAND") {
            const valid = /^[0-9]{11}$/.test(value);
            if (!valid) return { outcome: "FAIL", reason: "invalid_format", label: "PESEL must be 11 digits" };
            const lastDigit = value.slice(-1);
            if (lastDigit === "0") return { outcome: "MANUAL_REVIEW", reason: "manual_review", label: "eID verification needs manual review" };
            return { outcome: "PASS", reason: "verified", label: "eID / Trusted Profile identity verified" };
        }

        return { outcome: "FAIL", reason: "unsupported_country", label: "Unsupported country" };
    },

    /** PEP / sanctions declaration mock. */
    pepSanctions(isPep) {
        if (isPep === "yes") {
            return { outcome: "MANUAL_REVIEW", reason: "possible_hit", label: "PEP declaration flagged for manual review" };
        }
        return { outcome: "PASS", reason: "no_hit", label: "No PEP/sanctions hit" };
    },

    /** Credit bureau + affordability mock. */
    creditBureau(monthlyIncome, monthlyExpenses) {
        const income = Number(monthlyIncome);
        const expenses = Number(monthlyExpenses);

        if (!Number.isFinite(income) || income <= 0) {
            return { outcome: "FAIL", reason: "invalid_income", label: "Income figure not usable for scoring", disposable: null };
        }

        const disposable = income - (Number.isFinite(expenses) ? expenses : 0);

        if (disposable < 0) {
            return { outcome: "MANUAL_REVIEW", reason: "debt_flags", label: "Negative disposable income - manual review", disposable };
        }
        if (disposable < 300) {
            return { outcome: "MANUAL_REVIEW", reason: "low_disposable_income", label: "Low disposable income - manual review", disposable };
        }
        return { outcome: "PASS", reason: "decision_reason:affordable", label: "Affordability check passed", disposable };
    }
};

/* ---------------------------------------------------------------------
 * Flow configuration per country (Private individual only)
 * ------------------------------------------------------------------- */

const PRIVATE_FLOWS = {
    SWEDEN: {
        title: "Sweden \u2013 Private individual",
        idFieldLabel: "Personal identity number",
        idFieldPlaceholder: "YYYYMMDD-XXXX",
        idFieldName: "personalIdNumber",
        taxCountryDefault: "Sweden",
        steps: [
            {
                key: "identity",
                title: "Identity verification",
                description: "We use a BankID-style mock check to confirm your identity.",
                fields: [
                    { name: "fullName", label: "Full legal name", type: "text", required: true },
                    { name: "personalIdNumber", label: "Personal identity number", type: "text", required: true, placeholder: "YYYYMMDD-XXXX" }
                ],
                integration: (answers) => MockIntegrations.identity("SWEDEN", answers.personalIdNumber),
                integrationType: "IDENTITY"
            },
            {
                key: "contact",
                title: "Contact details & address",
                description: "Confirm how we can reach you and where you live.",
                fields: [
                    { name: "email", label: "Email address", type: "email", required: true },
                    { name: "phone", label: "Mobile number", type: "tel", required: true },
                    { name: "street", label: "Street address", type: "text", required: true },
                    { name: "postalCode", label: "Postal code", type: "text", required: true, pattern: "^[0-9]{3}\\s?[0-9]{2}$", patternHint: "e.g. 111 22" },
                    { name: "city", label: "City", type: "text", required: true }
                ]
            },
            {
                key: "consent",
                title: "Consent & declarations",
                description: "Legally required declarations before we can proceed.",
                fields: [
                    { name: "dataConsent", label: "I consent to processing of my personal data for onboarding purposes", type: "checkbox", required: true },
                    { name: "isPep", label: "Are you, or a close family member, a Politically Exposed Person (PEP)?", type: "radio", options: ["no", "yes"], required: true },
                    { name: "taxResidency", label: "Tax residency country", type: "select", options: ["Sweden", "Other EU", "Outside EU"], required: true }
                ],
                integration: (answers) => MockIntegrations.pepSanctions(answers.isPep),
                integrationType: "PEP_SANCTIONS"
            },
            {
                key: "affordability-input",
                title: "Employment, income & household",
                description: "Used to run the affordability check in the next step.",
                fields: [
                    { name: "employmentStatus", label: "Employment status", type: "select", options: ["Employed", "Self-employed", "Student", "Unemployed", "Retired"], required: true },
                    { name: "monthlyIncome", label: "Monthly net income (SEK)", type: "number", required: true, min: 0 },
                    { name: "monthlyExpenses", label: "Monthly housing & living costs (SEK)", type: "number", required: true, min: 0 },
                    { name: "householdSize", label: "Household size", type: "number", required: true, min: 1 }
                ]
            },
            {
                key: "credit-bureau",
                title: "Credit bureau & affordability decision",
                description: "We run a deterministic mock credit-bureau and affordability check using the figures you provided.",
                fields: [],
                integration: (answers) => MockIntegrations.creditBureau(answers.monthlyIncome, answers.monthlyExpenses),
                integrationType: "CREDIT_BUREAU"
            },
            { key: "review", title: "Review & submit", description: "Please review your details before submitting.", fields: [] }
        ]
    },

    SPAIN: {
        title: "Spain \u2013 Private individual",
        steps: [
            {
                key: "identity",
                title: "Identity verification",
                description: "We use a Clave/DNIe-style mock document check to confirm your identity.",
                fields: [
                    { name: "fullName", label: "Full legal name", type: "text", required: true },
                    { name: "idNumber", label: "DNI or NIE", type: "text", required: true, placeholder: "e.g. 12345678A or X1234567L" }
                ],
                integration: (answers) => MockIntegrations.identity("SPAIN", answers.idNumber),
                integrationType: "IDENTITY"
            },
            {
                key: "contact",
                title: "Contact details, province & address",
                description: "Confirm how we can reach you and your tax residency.",
                fields: [
                    { name: "email", label: "Email address", type: "email", required: true },
                    { name: "phone", label: "Mobile number", type: "tel", required: true },
                    { name: "province", label: "Province", type: "text", required: true },
                    { name: "street", label: "Address", type: "text", required: true },
                    { name: "taxResidency", label: "Tax residency country", type: "select", options: ["Spain", "Other EU", "Outside EU"], required: true }
                ]
            },
            {
                key: "consent",
                title: "Consent & declarations",
                description: "Legally required declarations before we can proceed.",
                fields: [
                    { name: "dataConsent", label: "I consent to processing of my personal data for onboarding purposes", type: "checkbox", required: true },
                    { name: "isPep", label: "Are you, or a close family member, a Politically Exposed Person (PEP)?", type: "radio", options: ["no", "yes"], required: true }
                ],
                integration: (answers) => MockIntegrations.pepSanctions(answers.isPep),
                integrationType: "PEP_SANCTIONS"
            },
            {
                key: "affordability-input",
                title: "Employment, income & dependants",
                description: "Used to run the affordability check in the next step.",
                fields: [
                    { name: "employmentStatus", label: "Employment status", type: "select", options: ["Employed", "Self-employed", "Student", "Unemployed", "Retired"], required: true },
                    { name: "monthlyIncome", label: "Monthly net income (EUR)", type: "number", required: true, min: 0 },
                    { name: "monthlyExpenses", label: "Monthly housing costs (EUR)", type: "number", required: true, min: 0 },
                    { name: "dependants", label: "Number of dependants", type: "number", required: true, min: 0 }
                ]
            },
            {
                key: "credit-bureau",
                title: "Credit bureau & affordability decision",
                description: "We run a deterministic mock credit-bureau and affordability check using the figures you provided.",
                fields: [],
                integration: (answers) => MockIntegrations.creditBureau(answers.monthlyIncome, answers.monthlyExpenses),
                integrationType: "CREDIT_BUREAU"
            },
            { key: "review", title: "Review & submit", description: "Please review your details before submitting.", fields: [] }
        ]
    },

    POLAND: {
        title: "Poland \u2013 Private individual",
        steps: [
            {
                key: "identity",
                title: "Identity verification",
                description: "We use an eID/Trusted Profile-style mock check to confirm your identity.",
                fields: [
                    { name: "fullName", label: "Full legal name", type: "text", required: true },
                    { name: "pesel", label: "PESEL", type: "text", required: true, placeholder: "11 digits" }
                ],
                integration: (answers) => MockIntegrations.identity("POLAND", answers.pesel),
                integrationType: "IDENTITY"
            },
            {
                key: "contact",
                title: "Contact details & registered address",
                description: "Confirm how we can reach you and where you're registered.",
                fields: [
                    { name: "email", label: "Email address", type: "email", required: true },
                    { name: "phone", label: "Mobile number", type: "tel", required: true },
                    { name: "street", label: "Registered address", type: "text", required: true },
                    { name: "city", label: "City", type: "text", required: true }
                ]
            },
            {
                key: "consent",
                title: "Consent & declarations",
                description: "Legally required declarations before we can proceed.",
                fields: [
                    { name: "dataConsent", label: "I consent to processing of my personal data for onboarding purposes", type: "checkbox", required: true },
                    { name: "isPep", label: "Are you, or a close family member, a Politically Exposed Person (PEP)?", type: "radio", options: ["no", "yes"], required: true }
                ],
                integration: (answers) => MockIntegrations.pepSanctions(answers.isPep),
                integrationType: "PEP_SANCTIONS"
            },
            {
                key: "affordability-input",
                title: "Employment, income & affordability",
                description: "Used to run the BIK-style credit-bureau check in the next step.",
                fields: [
                    { name: "employmentStatus", label: "Employment status", type: "select", options: ["Employed", "Self-employed", "Student", "Unemployed", "Retired"], required: true },
                    { name: "monthlyIncome", label: "Monthly net income (PLN)", type: "number", required: true, min: 0 },
                    { name: "monthlyExpenses", label: "Monthly living costs (PLN)", type: "number", required: true, min: 0 }
                ]
            },
            {
                key: "credit-bureau",
                title: "BIK-style credit bureau & decision",
                description: "We run a deterministic mock credit-bureau and affordability check using the figures you provided.",
                fields: [],
                integration: (answers) => MockIntegrations.creditBureau(answers.monthlyIncome, answers.monthlyExpenses),
                integrationType: "CREDIT_BUREAU"
            },
            { key: "review", title: "Review & submit", description: "Please review your details before submitting.", fields: [] }
        ]
    }
};

/** Field values that should never be shown/logged in raw form (masked in UI + audit trail). */
const SENSITIVE_FIELD_NAMES = new Set([
    "personalIdNumber",
    "idNumber",
    "pesel",
    "representativeId",
    "organisationNumber",
    "companyNif",
    "companyIdentifier",
    "iban"
]);

function maskSensitiveValue(value) {
    if (!value) return value;
    const str = String(value);
    if (str.length <= 4) return "*".repeat(str.length);
    return `${"*".repeat(str.length - 4)}${str.slice(-4)}`;
}

/**
 * Client-side flow definitions mirroring the "minimum flow matrix" from the
 * requirement doc. This is a placeholder used to preview the adaptive
 * journey before a backend flow engine exists. Once the API is built, this
 * static config will be replaced by data fetched from the server.
 *
 * Keyed by "<COUNTRY>_<CUSTOMER_TYPE>" so adding a new market/type only
 * means adding a new entry here, not branching logic elsewhere.
 */
const FLOWS = {
    SWEDEN_PRIVATE: {
        title: "Sweden \u2013 Private individual",
        steps: [
            "Collect personal identity number & initiate BankID-style identity check",
            "Confirm contact details and address",
            "Capture consent, PEP/sanctions declaration, and tax residency",
            "Collect employment, income, household and affordability inputs",
            "Run credit-bureau decision and affordability rules",
            "Review summary, accept terms, submit application"
        ]
    },
    SWEDEN_BUSINESS: {
        title: "Sweden \u2013 Business",
        steps: [
            "Collect organisation number, legal name and legal form",
            "Run Bolagsverket-style company registry lookup",
            "Confirm authorised representative and signatory rights",
            "Collect beneficial owners and verify with BankID-style KYC mock",
            "Capture business activity, turnover, purpose and expected usage",
            "Run KYB, sanctions/PEP and business-credit decision, then review/sign"
        ]
    },
    SPAIN_PRIVATE: {
        title: "Spain \u2013 Private individual",
        steps: [
            "Collect DNI/NIE & initiate Clave/DNIe/document-verification mock",
            "Confirm contact details, province, address, and tax residency",
            "Capture consent and PEP/sanctions declaration",
            "Collect employment, income, housing costs and dependants",
            "Run credit-bureau and affordability decision",
            "Review summary, accept terms, submit application"
        ]
    },
    SPAIN_BUSINESS: {
        title: "Spain \u2013 Business",
        steps: [
            "Collect company NIF, legal form and registered address",
            "Run Registro Mercantil / tax-status-style lookup",
            "Verify legal representative using DNI/NIE identity mock",
            "Collect beneficial owners and ownership percentages",
            "Capture sector, turnover, VAT/tax details and expected usage",
            "Run KYB, sanctions/PEP, business credit and IBAN verification, then review/sign"
        ]
    },
    POLAND_PRIVATE: {
        title: "Poland \u2013 Private individual",
        steps: [
            "Collect PESEL & initiate eID/Trusted Profile/mObywatel-style identity mock",
            "Confirm contact details and registered address",
            "Capture consent and PEP/sanctions declaration",
            "Collect employment, income and affordability information",
            "Run BIK-style credit-bureau mock and decision rules",
            "Review summary, accept terms, submit application"
        ]
    },
    POLAND_BUSINESS: {
        title: "Poland \u2013 Business",
        steps: [
            "Collect NIP, REGON or KRS/CEIDG identifier and legal form",
            "Run CEIDG/KRS-style registry lookup",
            "Confirm board member or sole proprietor authority",
            "Collect beneficial owners and verify identity/risk",
            "Capture VAT/tax status, business activity and expected usage",
            "Run KYB, sanctions/PEP, business credit and bank-account validation, then review/sign"
        ]
    }
};


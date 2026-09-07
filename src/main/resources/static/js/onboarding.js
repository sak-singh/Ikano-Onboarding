/**
 * Handles the country/account-type selector interaction and drives the
 * onboarding journey.
 *
 * - PRIVATE flows are now persisted through backend APIs:
 *   - POST /api/private/sessions
 *   - POST /api/private/sessions/{id}/steps/{stepKey}
 *   - POST /api/private/sessions/{id}/submit
 * - BUSINESS flows are now persisted through backend APIs:
 *   - POST /api/business/sessions
 *   - POST /api/business/sessions/{id}/steps/{stepKey}
 *   - POST /api/business/sessions/{id}/submit
 */
(function () {
    const state = {
        country: null,
        customerType: null
    };

    /** Per-application runtime state for the interactive private flow. */
    const privateFlowState = {
        sessionId: null,
        stepIndex: 0,
        answers: {},
        integrationResults: [], // { stepKey, integrationType, outcome, reason, label, timestamp }
        auditTrail: [] // { timestamp, message }
    };

    const businessFlowState = {
        sessionId: null,
        stepIndex: 0,
        answers: {},
        integrationResults: [],
        auditTrail: [],
        flow: null,
        country: null
    };

    async function apiJson(url, options) {
        const response = await fetch(url, options);
        let payload = null;
        try {
            payload = await response.json();
        } catch (e) {
            payload = null;
        }
        if (!response.ok) {
            const message = payload && payload.message ? payload.message : `Request failed (${response.status})`;
            throw new Error(message);
        }
        return payload;
    }

    function prettyOutcome(value) {
        return value ? value.replace("_", " ") : "";
    }

    const countryOptions = document.getElementById("country-options");
    const typeOptions = document.getElementById("type-options");
    const countryError = document.getElementById("country-error");
    const typeError = document.getElementById("type-error");
    const form = document.getElementById("selector-form");

    const selectorCard = document.getElementById("selector-card");
    const flowCard = document.getElementById("flow-card");
    const flowBadge = document.getElementById("flow-badge");
    const flowTitle = document.getElementById("flow-title");
    const stepList = document.getElementById("step-list");
    const privateFlowContainer = document.getElementById("private-flow-container");
    const progressFill = document.getElementById("progress-fill");
    const progressLabel = document.getElementById("progress-label");
    const backBtn = document.getElementById("back-btn");
    const heroSection = document.getElementById("hero-section");

    const COUNTRY_LABELS = { SWEDEN: "\ud83c\uddf8\ud83c\uddea Sweden", SPAIN: "\ud83c\uddea\ud83c\uddf8 Spain", POLAND: "\ud83c\uddf5\ud83c\uddf1 Poland" };
    const TYPE_LABELS = { PRIVATE: "Private individual", BUSINESS: "Business" };

    function wireOptionGroup(container, onSelect) {
        container.querySelectorAll(".option-btn").forEach((btn) => {
            btn.addEventListener("click", () => {
                container.querySelectorAll(".option-btn").forEach((b) => b.classList.remove("selected"));
                btn.classList.add("selected");
                onSelect(btn.dataset.value);
            });
        });
    }

    wireOptionGroup(countryOptions, (value) => {
        state.country = value;
        countryError.textContent = "";
    });

    wireOptionGroup(typeOptions, (value) => {
        state.customerType = value;
        typeError.textContent = "";
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        let valid = true;
        if (!state.country) {
            countryError.textContent = "Please select a country to continue.";
            valid = false;
        }
        if (!state.customerType) {
            typeError.textContent = "Please select an account type to continue.";
            valid = false;
        }
        if (!valid) {
            return;
        }

        await startFlow(state.country, state.customerType);
    });

    backBtn.addEventListener("click", () => {
        flowCard.classList.add("hidden");
        selectorCard.classList.remove("hidden");
        heroSection.classList.remove("hidden");
    });

    async function startFlow(country, customerType) {
        flowBadge.textContent = `${COUNTRY_LABELS[country] || country} \u00b7 ${TYPE_LABELS[customerType] || customerType}`;

        selectorCard.classList.add("hidden");
        heroSection.classList.add("hidden");
        flowCard.classList.remove("hidden");

        if (customerType === "PRIVATE") {
            stepList.innerHTML = "";
            stepList.classList.add("hidden");
            privateFlowContainer.classList.remove("hidden");

            privateFlowState.sessionId = null;
            privateFlowState.stepIndex = 0;
            privateFlowState.answers = {};
            privateFlowState.integrationResults = [];
            privateFlowState.auditTrail = [];

            try {
                const created = await apiJson("/api/private/sessions", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ country, customerType })
                });
                privateFlowState.sessionId = created.sessionId;
                logAudit(`Application started on backend (session: ${privateFlowState.sessionId})`);
            } catch (error) {
                typeError.textContent = `Failed to create session: ${error.message}`;
                flowCard.classList.add("hidden");
                selectorCard.classList.remove("hidden");
                heroSection.classList.remove("hidden");
                return;
            }

            const flow = PRIVATE_FLOWS[country];
            flowTitle.textContent = flow ? flow.title : "Private individual onboarding";
            renderPrivateStep(country);
            return;
        }

        // BUSINESS: interactive backend-driven journey.
        stepList.innerHTML = "";
        stepList.classList.add("hidden");
        privateFlowContainer.classList.remove("hidden");

        businessFlowState.sessionId = null;
        businessFlowState.stepIndex = 0;
        businessFlowState.answers = {};
        businessFlowState.integrationResults = [];
        businessFlowState.auditTrail = [];
        businessFlowState.flow = null;
        businessFlowState.country = country;

        try {
            const [created, flow] = await Promise.all([
                apiJson("/api/business/sessions", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ country, customerType })
                }),
                apiJson(`/api/business/${country}/flow`, { method: "GET" })
            ]);
            businessFlowState.sessionId = created.sessionId;
            businessFlowState.flow = flow;
            flowTitle.textContent = flow.title || "Business onboarding";
            businessLogAudit(`Business application started (session: ${businessFlowState.sessionId})`);
            renderBusinessStep();
        } catch (error) {
            typeError.textContent = `Failed to create business session: ${error.message}`;
            flowCard.classList.add("hidden");
            selectorCard.classList.remove("hidden");
            heroSection.classList.remove("hidden");
        }
    }

    /* ------------------------------------------------------------------
     * BUSINESS interactive step engine
     * ---------------------------------------------------------------- */

    function businessLogAudit(message) {
        businessFlowState.auditTrail.push({ timestamp: new Date().toISOString(), message });
    }

    function renderBusinessStep() {
        const flow = businessFlowState.flow;
        const step = flow.steps[businessFlowState.stepIndex];
        updateProgress(businessFlowState.stepIndex, flow.steps.length);

        if (step.key === "review") {
            renderBusinessReviewStep(step);
            return;
        }

        const fieldsHtml = step.fields
            .filter((f) => f.type !== "checkbox")
            .map((field) => `
                <div class="form-field">
                    <label class="form-label">${field.label}${field.required ? " *" : ""}</label>
                    ${fieldInputHtml(field, businessFlowState.answers[field.name])}
                    ${field.patternHint ? `<span class="form-hint">${field.patternHint}</span>` : ""}
                    <p class="field-error" data-error-for="${field.name}"></p>
                </div>`)
            .join("");

        const checkboxFieldsHtml = step.fields
            .filter((f) => f.type === "checkbox")
            .map((field) => `
                <div class="form-field">
                    ${fieldInputHtml(field, businessFlowState.answers[field.name])}
                    <p class="field-error" data-error-for="${field.name}"></p>
                </div>`)
            .join("");

        privateFlowContainer.innerHTML = `
            <div class="step-panel">
                <h3 class="step-panel-title">${step.title}</h3>
                <p class="step-panel-desc">${step.description}</p>
                <form id="business-step-form" novalidate>
                    ${fieldsHtml}
                    ${checkboxFieldsHtml}
                    <div id="business-integration-slot"></div>
                    <div class="step-actions">
                        ${businessFlowState.stepIndex > 0 ? '<button type="button" class="ghost-btn" id="business-step-back-btn">&larr; Back</button>' : "<span></span>"}
                        <button type="submit" class="primary-btn">Continue <span class="btn-arrow">&rarr;</span></button>
                    </div>
                </form>
            </div>`;

        const formEl = document.getElementById("business-step-form");
        const backEl = document.getElementById("business-step-back-btn");
        const integrationSlot = document.getElementById("business-integration-slot");

        if (backEl) {
            backEl.addEventListener("click", () => {
                businessFlowState.stepIndex -= 1;
                renderBusinessStep();
            });
        }

        formEl.addEventListener("submit", async (event) => {
            event.preventDefault();
            let valid = true;
            const stepAnswers = {};

            step.fields.forEach((field) => {
                const errorEl = formEl.querySelector(`[data-error-for="${field.name}"]`);
                if (!errorEl) return;
                errorEl.textContent = "";

                let rawValue;
                if (field.type === "checkbox") {
                    rawValue = formEl.elements[field.name].checked;
                } else if (field.type === "radio") {
                    const checked = formEl.querySelector(`input[name="${field.name}"]:checked`);
                    rawValue = checked ? checked.value : "";
                } else {
                    rawValue = formEl.elements[field.name] ? formEl.elements[field.name].value : "";
                }

                if (field.required) {
                    const empty = field.type === "checkbox" ? rawValue !== true : !rawValue && rawValue !== 0;
                    if (empty) {
                        errorEl.textContent = "This field is required.";
                        valid = false;
                        return;
                    }
                }
                if (field.pattern && rawValue && !new RegExp(field.pattern).test(rawValue)) {
                    errorEl.textContent = "Please check the format.";
                    valid = false;
                    return;
                }

                businessFlowState.answers[field.name] = rawValue;
                stepAnswers[field.name] = rawValue;
            });

            if (!valid) return;

            try {
                const response = await apiJson(`/api/business/sessions/${businessFlowState.sessionId}/steps/${step.key}`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ answers: stepAnswers })
                });

                if (response.integrationType && response.outcome) {
                    const record = {
                        stepKey: response.stepKey,
                        integrationType: response.integrationType,
                        outcome: response.outcome,
                        reason: response.reason,
                        label: response.label || prettyOutcome(response.outcome),
                        timestamp: new Date().toISOString()
                    };
                    businessFlowState.integrationResults.push(record);
                    businessLogAudit(`Integration "${record.integrationType}" returned ${record.outcome} (${record.reason || "n/a"})`);
                    integrationSlot.innerHTML = renderIntegrationPanel(record);
                }

                businessLogAudit(`Step "${step.key}" completed`);
                businessFlowState.stepIndex = Number(response.nextStepIndex);
                renderBusinessStep();
            } catch (error) {
                integrationSlot.innerHTML = `
                    <div class="integration-panel tone-fail">
                        <span class="integration-outcome">Step failed</span>
                        <span class="integration-label">${error.message}</span>
                    </div>`;
            }
        });
    }

    function renderBusinessReviewStep(step) {
        const answerRows = Object.entries(businessFlowState.answers)
            .map(([key, value]) => {
                const displayValue = SENSITIVE_FIELD_NAMES.has(key)
                    ? maskSensitiveValue(value)
                    : typeof value === "boolean"
                        ? value ? "Yes" : "No"
                        : value;
                return `<tr><td>${key}</td><td>${displayValue}</td></tr>`;
            })
            .join("");

        const integrationRows = businessFlowState.integrationResults
            .map((r) => `
                <tr>
                    <td>${r.integrationType}</td>
                    <td><span class="outcome-pill outcome-${r.outcome.toLowerCase()}">${r.outcome.replace("_", " ")}</span></td>
                    <td>${r.reason}</td>
                </tr>`)
            .join("");

        privateFlowContainer.innerHTML = `
            <div class="step-panel">
                <h3 class="step-panel-title">${step.title}</h3>
                <p class="step-panel-desc">${step.description}</p>

                <h4 class="review-subheading">Your answers</h4>
                <table class="review-table"><tbody>${answerRows}</tbody></table>

                <h4 class="review-subheading">Integration results</h4>
                <table class="review-table"><tbody>${integrationRows}</tbody></table>

                <div id="business-decision-slot"></div>

                <div class="step-actions">
                    <button type="button" class="ghost-btn" id="business-review-back-btn">&larr; Back</button>
                    <button type="button" class="primary-btn" id="business-submit-btn">Submit application</button>
                </div>
            </div>`;

        document.getElementById("business-review-back-btn").addEventListener("click", () => {
            businessFlowState.stepIndex -= 1;
            renderBusinessStep();
        });

        document.getElementById("business-submit-btn").addEventListener("click", async () => {
            try {
                const response = await apiJson(`/api/business/sessions/${businessFlowState.sessionId}/submit`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" }
                });

                const decision = response.decision;
                const toneClass = decision === "APPROVED" ? "tone-pass" : decision === "MANUAL_REVIEW" ? "tone-review" : "tone-fail";
                const decisionLabel =
                    decision === "APPROVED" ? "Application approved" : decision === "MANUAL_REVIEW" ? "Referred to manual review" : "Application rejected";

                const reasons = response.reasons && response.reasons.length ? `Reasons: ${response.reasons.join(", ")}` : "All checks passed";
                const auditRows = (response.auditTrail || [])
                    .map((e) => `<li><span class="audit-time">${new Date(e.timestamp).toLocaleTimeString()}</span><span>${e.message}</span></li>`)
                    .join("");

                document.getElementById("business-decision-slot").innerHTML = `
                    <div class="integration-panel ${toneClass}">
                        <span class="integration-outcome">${decisionLabel}</span>
                        <span class="integration-label">${reasons}</span>
                    </div>
                    <h4 class="review-subheading">Audit trail</h4>
                    <ul class="audit-list">${auditRows}</ul>`;
            } catch (error) {
                document.getElementById("business-decision-slot").innerHTML = `
                    <div class="integration-panel tone-fail">
                        <span class="integration-outcome">Submit failed</span>
                        <span class="integration-label">${error.message}</span>
                    </div>`;
            }

            document.getElementById("business-submit-btn").disabled = true;
        });
    }

    /* ------------------------------------------------------------------
     * PRIVATE interactive step engine
     * ---------------------------------------------------------------- */

    function logAudit(message) {
        privateFlowState.auditTrail.push({ timestamp: new Date().toISOString(), message });
    }

    function updateProgress(stepIndex, totalSteps) {
        const percent = Math.round(((stepIndex + 1) / totalSteps) * 100);
        progressFill.style.width = `${percent}%`;
        progressLabel.textContent = `Step ${stepIndex + 1} of ${totalSteps}`;
    }

    function fieldInputHtml(field, currentValue) {
        const requiredAttr = field.required ? "required" : "";
        const patternAttr = field.pattern ? `pattern="${field.pattern}"` : "";
        const minAttr = field.min !== undefined ? `min="${field.min}"` : "";

        if (field.type === "select") {
            const options = field.options
                .map((opt) => `<option value="${opt}" ${currentValue === opt ? "selected" : ""}>${opt}</option>`)
                .join("");
            return `
                <select name="${field.name}" ${requiredAttr}>
                    <option value="" disabled ${!currentValue ? "selected" : ""}>Select...</option>
                    ${options}
                </select>`;
        }

        if (field.type === "radio") {
            return field.options
                .map(
                    (opt) => `
                    <label class="radio-option">
                        <input type="radio" name="${field.name}" value="${opt}" ${requiredAttr} ${currentValue === opt ? "checked" : ""} />
                        <span>${opt === "yes" ? "Yes" : opt === "no" ? "No" : opt}</span>
                    </label>`
                )
                .join("");
        }

        if (field.type === "checkbox") {
            return `
                <label class="checkbox-option">
                    <input type="checkbox" name="${field.name}" ${requiredAttr} ${currentValue ? "checked" : ""} />
                    <span>${field.label}</span>
                </label>`;
        }

        const valueAttr = currentValue !== undefined && currentValue !== null ? `value="${currentValue}"` : "";
        return `<input type="${field.type}" name="${field.name}" ${valueAttr} placeholder="${field.placeholder || ""}" ${requiredAttr} ${patternAttr} ${minAttr} />`;
    }

    function renderIntegrationPanel(result) {
        const toneClass = result.outcome === "PASS" ? "tone-pass" : result.outcome === "MANUAL_REVIEW" ? "tone-review" : "tone-fail";
        const outcomeLabel = result.outcome === "PASS" ? "Passed" : result.outcome === "MANUAL_REVIEW" ? "Manual review" : "Failed";
        return `
            <div class="integration-panel ${toneClass}">
                <span class="integration-outcome">${outcomeLabel}</span>
                <span class="integration-label">${result.label}</span>
            </div>`;
    }

    function renderPrivateStep(country) {
        const flow = PRIVATE_FLOWS[country];
        const step = flow.steps[privateFlowState.stepIndex];
        updateProgress(privateFlowState.stepIndex, flow.steps.length);

        if (step.key === "review") {
            renderReviewStep(country, flow, step);
            return;
        }

        const fieldsHtml = step.fields
            .filter((f) => f.type !== "checkbox")
            .map(
                (field) => `
                <div class="form-field">
                    <label class="form-label">${field.label}${field.required ? " *" : ""}</label>
                    ${fieldInputHtml(field, privateFlowState.answers[field.name])}
                    ${field.patternHint ? `<span class="form-hint">${field.patternHint}</span>` : ""}
                    <p class="field-error" data-error-for="${field.name}"></p>
                </div>`
            )
            .join("");

        const checkboxFieldsHtml = step.fields
            .filter((f) => f.type === "checkbox")
            .map(
                (field) => `
                <div class="form-field">
                    ${fieldInputHtml(field, privateFlowState.answers[field.name])}
                    <p class="field-error" data-error-for="${field.name}"></p>
                </div>`
            )
            .join("");

        privateFlowContainer.innerHTML = `
            <div class="step-panel">
                <h3 class="step-panel-title">${step.title}</h3>
                <p class="step-panel-desc">${step.description}</p>
                <form id="step-form" novalidate>
                    ${fieldsHtml}
                    ${checkboxFieldsHtml}
                    <div id="integration-slot"></div>
                    <div class="step-actions">
                        ${privateFlowState.stepIndex > 0 ? '<button type="button" class="ghost-btn" id="step-back-btn">&larr; Back</button>' : "<span></span>"}
                        <button type="submit" class="primary-btn" id="step-next-btn">
                            ${step.integration ? "Run check & continue" : "Continue"}
                            <span class="btn-arrow">&rarr;</span>
                        </button>
                    </div>
                </form>
            </div>`;

        const stepForm = document.getElementById("step-form");
        const stepBackBtn = document.getElementById("step-back-btn");
        const integrationSlot = document.getElementById("integration-slot");

        if (stepBackBtn) {
            stepBackBtn.addEventListener("click", () => {
                privateFlowState.stepIndex -= 1;
                renderPrivateStep(country);
            });
        }

        stepForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            let valid = true;
            const stepAnswers = {};

            step.fields.forEach((field) => {
                const errorEl = stepForm.querySelector(`[data-error-for="${field.name}"]`);
                if (!errorEl) return;
                errorEl.textContent = "";

                let rawValue;
                if (field.type === "checkbox") {
                    rawValue = stepForm.elements[field.name].checked;
                } else if (field.type === "radio") {
                    const checked = stepForm.querySelector(`input[name="${field.name}"]:checked`);
                    rawValue = checked ? checked.value : "";
                } else {
                    rawValue = stepForm.elements[field.name] ? stepForm.elements[field.name].value : "";
                }

                if (field.required) {
                    const empty = field.type === "checkbox" ? rawValue !== true : !rawValue && rawValue !== 0;
                    if (empty) {
                        errorEl.textContent = "This field is required.";
                        valid = false;
                        return;
                    }
                }

                if (field.pattern && rawValue && !new RegExp(field.pattern).test(rawValue)) {
                    errorEl.textContent = "Please check the format.";
                    valid = false;
                    return;
                }

                privateFlowState.answers[field.name] = rawValue;
                stepAnswers[field.name] = rawValue;
            });

            if (!valid) return;

            try {
                const response = await apiJson(`/api/private/sessions/${privateFlowState.sessionId}/steps/${step.key}`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ answers: stepAnswers })
                });

                if (response.integrationType && response.outcome) {
                    const record = {
                        stepKey: response.stepKey,
                        integrationType: response.integrationType,
                        outcome: response.outcome,
                        reason: response.reason,
                        label: response.label || prettyOutcome(response.outcome),
                        timestamp: new Date().toISOString()
                    };
                    privateFlowState.integrationResults.push(record);
                    logAudit(`Integration "${record.integrationType}" returned ${record.outcome} (${record.reason || "n/a"})`);
                    integrationSlot.innerHTML = renderIntegrationPanel(record);
                }

                logAudit(`Step "${step.key}" completed`);
                privateFlowState.stepIndex = Number(response.nextStepIndex);
                renderPrivateStep(country);
            } catch (error) {
                integrationSlot.innerHTML = `
                    <div class="integration-panel tone-fail">
                        <span class="integration-outcome">Step failed</span>
                        <span class="integration-label">${error.message}</span>
                    </div>`;
            }
        });
    }

    function computeFinalDecision() {
        const outcomes = privateFlowState.integrationResults.map((r) => r.outcome);
        if (outcomes.includes("FAIL")) {
            return { outcome: "REJECTED", reasons: privateFlowState.integrationResults.filter((r) => r.outcome === "FAIL").map((r) => r.reason) };
        }
        if (outcomes.includes("MANUAL_REVIEW")) {
            return { outcome: "MANUAL_REVIEW", reasons: privateFlowState.integrationResults.filter((r) => r.outcome === "MANUAL_REVIEW").map((r) => r.reason) };
        }
        return { outcome: "APPROVED", reasons: [] };
    }

    function renderReviewStep(country, flow, step) {
        const answerRows = Object.entries(privateFlowState.answers)
            .map(([key, value]) => {
                const displayValue = SENSITIVE_FIELD_NAMES.has(key)
                    ? maskSensitiveValue(value)
                    : typeof value === "boolean"
                        ? value
                            ? "Yes"
                            : "No"
                        : value;
                return `<tr><td>${key}</td><td>${displayValue}</td></tr>`;
            })
            .join("");

        const integrationRows = privateFlowState.integrationResults
            .map(
                (r) => `
                <tr>
                    <td>${r.integrationType}</td>
                    <td><span class="outcome-pill outcome-${r.outcome.toLowerCase()}">${r.outcome.replace("_", " ")}</span></td>
                    <td>${r.reason}</td>
                </tr>`
            )
            .join("");

        privateFlowContainer.innerHTML = `
            <div class="step-panel">
                <h3 class="step-panel-title">${step.title}</h3>
                <p class="step-panel-desc">${step.description}</p>

                <h4 class="review-subheading">Your answers</h4>
                <table class="review-table"><tbody>${answerRows}</tbody></table>

                <h4 class="review-subheading">Integration results</h4>
                <table class="review-table"><tbody>${integrationRows}</tbody></table>

                <div id="decision-slot"></div>

                <div class="step-actions">
                    <button type="button" class="ghost-btn" id="step-back-btn">&larr; Back</button>
                    <button type="button" class="primary-btn" id="submit-application-btn">Submit application</button>
                </div>
            </div>`;

        document.getElementById("step-back-btn").addEventListener("click", () => {
            privateFlowState.stepIndex -= 1;
            renderPrivateStep(country);
        });

        document.getElementById("submit-application-btn").addEventListener("click", async () => {
            try {
                const response = await apiJson(`/api/private/sessions/${privateFlowState.sessionId}/submit`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" }
                });

                const decision = response.decision;
                const toneClass = decision === "APPROVED" ? "tone-pass" : decision === "MANUAL_REVIEW" ? "tone-review" : "tone-fail";
                const decisionLabel =
                    decision === "APPROVED" ? "Application approved" : decision === "MANUAL_REVIEW" ? "Referred to manual review" : "Application rejected";

                const reasons = response.reasons && response.reasons.length ? `Reasons: ${response.reasons.join(", ")}` : "All checks passed";
                const auditRows = (response.auditTrail || [])
                    .map((e) => `<li><span class="audit-time">${new Date(e.timestamp).toLocaleTimeString()}</span><span>${e.message}</span></li>`)
                    .join("");

                document.getElementById("decision-slot").innerHTML = `
                    <div class="integration-panel ${toneClass}">
                        <span class="integration-outcome">${decisionLabel}</span>
                        <span class="integration-label">${reasons}</span>
                    </div>
                    <h4 class="review-subheading">Audit trail</h4>
                    <ul class="audit-list">${auditRows}</ul>`;
            } catch (error) {
                document.getElementById("decision-slot").innerHTML = `
                    <div class="integration-panel tone-fail">
                        <span class="integration-outcome">Submit failed</span>
                        <span class="integration-label">${error.message}</span>
                    </div>`;
            }

            document.getElementById("submit-application-btn").disabled = true;
        });
    }
})();

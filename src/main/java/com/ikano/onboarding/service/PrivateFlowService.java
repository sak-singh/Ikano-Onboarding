package com.ikano.onboarding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikano.onboarding.config.FieldDefinition;
import com.ikano.onboarding.config.PrivateFlowConfig;
import com.ikano.onboarding.config.StepDefinition;
import com.ikano.onboarding.domain.*;
import com.ikano.onboarding.dto.*;
import com.ikano.onboarding.repository.AuditEventRepository;
import com.ikano.onboarding.repository.OnboardingSessionRepository;
import com.ikano.onboarding.repository.SampleOnboardingUserDetailsRepository;
import com.ikano.onboarding.service.integration.CreditBureauMockService;
import com.ikano.onboarding.service.integration.IdentityMockService;
import com.ikano.onboarding.service.integration.IntegrationResult;
import com.ikano.onboarding.service.integration.PepSanctionsMockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Orchestrates the private-individual onboarding journey: creates
 * sessions, validates and persists step answers, invokes the relevant
 * mock integration for each step, and computes the final decision.
 * <p>
 * No authentication is applied here by design (see requirement.md: "No
 * production authentication system"). Traceability comes from the
 * generated {@code sessionId} (UUID) plus the {@link AuditEvent} trail.
 */
@Service
public class PrivateFlowService {

    private final PrivateFlowConfig flowConfig;
    private final OnboardingSessionRepository sessionRepository;
    private final AuditEventRepository auditEventRepository;
    private final SampleOnboardingUserDetailsRepository sampleOnboardingUserDetailsRepository;
    private final IdentityMockService identityMockService;
    private final PepSanctionsMockService pepSanctionsMockService;
    private final CreditBureauMockService creditBureauMockService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PrivateFlowService(PrivateFlowConfig flowConfig,
                               OnboardingSessionRepository sessionRepository,
                               AuditEventRepository auditEventRepository,
                               SampleOnboardingUserDetailsRepository sampleOnboardingUserDetailsRepository,
                               IdentityMockService identityMockService,
                               PepSanctionsMockService pepSanctionsMockService,
                               CreditBureauMockService creditBureauMockService) {
        this.flowConfig = flowConfig;
        this.sessionRepository = sessionRepository;
        this.auditEventRepository = auditEventRepository;
        this.sampleOnboardingUserDetailsRepository = sampleOnboardingUserDetailsRepository;
        this.identityMockService = identityMockService;
        this.pepSanctionsMockService = pepSanctionsMockService;
        this.creditBureauMockService = creditBureauMockService;
    }

    public com.ikano.onboarding.config.FlowDefinition getFlow(Country country) {
        return flowConfig.get(country);
    }

    @Transactional
    public CreateSessionResponse createSession(Country country, CustomerType customerType) {
        if (customerType != CustomerType.PRIVATE) {
            throw new IllegalArgumentException("Only PRIVATE customer type is supported by this API for now");
        }

        OnboardingSession session = new OnboardingSession(country, customerType);
        session = sessionRepository.save(session);
        saveUserDetailsSnapshot(session);

        recordAudit(session.getId(), null, null, null,
                "Application started for " + country + " / " + customerType);

        return new CreateSessionResponse(session.getId(), flowConfig.get(country));
    }

    @Transactional
    public StepSubmitResponse submitStep(UUID sessionId, String stepKey, Map<String, Object> submittedAnswers) {
        OnboardingSession session = requireSession(sessionId);
        var flow = flowConfig.get(session.getCountry());
        StepDefinition step = flow.stepAt(session.getCurrentStepIndex());

        if (!step.key().equals(stepKey)) {
            throw new IllegalStateException("Step out of order: expected '" + step.key() + "' but got '" + stepKey + "'");
        }

        validateFields(step, submittedAnswers);

        Map<String, Object> allAnswers = readAnswers(session);
        allAnswers.putAll(submittedAnswers);
        session.setAnswersJson(writeAnswers(allAnswers));

        Outcome outcome = null;
        String reason = null;
        String label = null;

        if (step.integrationType() != null) {
            IntegrationResult result = runIntegration(session.getCountry(), step, allAnswers);
            outcome = result.outcome();
            reason = result.reason();
            label = result.label();
            recordAudit(sessionId, step.key(), step.integrationType(), outcome,
                    "Integration " + step.integrationType() + " on step '" + step.key() + "' returned " + outcome + " (" + reason + ")");
        }

        recordAudit(sessionId, step.key(), null, null, "Step '" + step.key() + "' completed");

        int nextIndex = session.getCurrentStepIndex() + 1;
        session.setCurrentStepIndex(nextIndex);
        sessionRepository.save(session);
        saveUserDetailsSnapshot(session);

        boolean completed = nextIndex >= flow.steps().size();
        return new StepSubmitResponse(step.key(), step.integrationType(), outcome, reason, label, nextIndex, completed);
    }

    @Transactional(readOnly = true)
    public ReviewResponse getReview(UUID sessionId) {
        OnboardingSession session = requireSession(sessionId);
        Map<String, Object> answers = readAnswers(session);

        Map<String, Object> masked = new LinkedHashMap<>();
        answers.forEach((key, value) -> masked.put(key, SensitiveDataMasker.isSensitive(key) ? SensitiveDataMasker.mask(value) : value));

        List<IntegrationResultView> integrationResults = auditEventRepository.findBySessionIdOrderByTimestampAsc(sessionId).stream()
                .filter(e -> e.getIntegrationType() != null)
                .map(e -> new IntegrationResultView(e.getStepKey(), e.getIntegrationType(), e.getOutcome(),
                        extractReason(e.getDescription()), e.getDescription(), e.getTimestamp()))
                .toList();

        return new ReviewResponse(session.getStatus(), masked, integrationResults);
    }

    @Transactional
    public SubmitResponse submitApplication(UUID sessionId) {
        OnboardingSession session = requireSession(sessionId);

        List<Outcome> outcomes = auditEventRepository.findBySessionIdOrderByTimestampAsc(sessionId).stream()
                .filter(e -> e.getOutcome() != null)
                .map(com.ikano.onboarding.domain.AuditEvent::getOutcome)
                .toList();

        SessionStatus decision;
        List<String> reasons = new ArrayList<>();

        if (outcomes.contains(Outcome.FAIL)) {
            decision = SessionStatus.REJECTED;
        } else if (outcomes.contains(Outcome.MANUAL_REVIEW)) {
            decision = SessionStatus.MANUAL_REVIEW;
        } else {
            decision = SessionStatus.APPROVED;
        }

        session.setStatus(decision);
        sessionRepository.save(session);
        saveUserDetailsSnapshot(session);
        recordAudit(sessionId, null, null, null, "Decision made: " + decision);

        List<SubmitResponse.AuditEntryView> auditTrail = auditEventRepository.findBySessionIdOrderByTimestampAsc(sessionId).stream()
                .map(e -> new SubmitResponse.AuditEntryView(e.getTimestamp(), e.getDescription()))
                .toList();

        return new SubmitResponse(decision, reasons, auditTrail);
    }

    /* ------------------------------------------------------------------ */

    private IntegrationResult runIntegration(Country country, StepDefinition step, Map<String, Object> answers) {
        return switch (step.integrationType()) {
            case IDENTITY -> {
                String idField = country == Country.SWEDEN ? "personalIdNumber"
                        : country == Country.POLAND ? "pesel" : "idNumber";
                yield identityMockService.check(country, String.valueOf(answers.get(idField)));
            }
            case PEP_SANCTIONS -> pepSanctionsMockService.check("yes".equals(answers.get("isPep")));
            case CREDIT_BUREAU -> creditBureauMockService.check(
                    toDouble(answers.get("monthlyIncome")), toDouble(answers.get("monthlyExpenses")));
            case REGISTRY, BANK_ACCOUNT -> throw new UnsupportedOperationException(
                    step.integrationType() + " is not used by the private-individual flow (business onboarding only)");
        };
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void validateFields(StepDefinition step, Map<String, Object> answers) {
        for (FieldDefinition field : step.fields()) {
            Object value = answers.get(field.name());
            if (field.required()) {
                boolean empty = value == null || String.valueOf(value).isBlank();
                if ("checkbox".equals(field.type())) {
                    empty = !Boolean.TRUE.equals(value) && !"true".equals(String.valueOf(value));
                }
                if (empty) {
                    throw new IllegalArgumentException("Field '" + field.name() + "' is required");
                }
            }
            if (field.pattern() != null && value != null && !String.valueOf(value).isBlank()
                    && !String.valueOf(value).matches(field.pattern())) {
                throw new IllegalArgumentException("Field '" + field.name() + "' does not match required format");
            }
        }
    }

    private OnboardingSession requireSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    }

    private void recordAudit(UUID sessionId, String stepKey, IntegrationType integrationType, Outcome outcome, String description) {
        auditEventRepository.save(new com.ikano.onboarding.domain.AuditEvent(sessionId, stepKey, integrationType, outcome, description));
    }

    /** Keeps a denormalized copy of all user-entered data for simple table inspection/reporting. */
    private void saveUserDetailsSnapshot(OnboardingSession session) {
        SampleOnboardingUserDetails row = sampleOnboardingUserDetailsRepository
                .findBySessionId(session.getId())
                .orElseGet(() -> new SampleOnboardingUserDetails(session.getId()));
        row.updateFromSession(session);
        sampleOnboardingUserDetailsRepository.save(row);
    }

    private String extractReason(String description) {
        int idx = description.indexOf('(');
        if (idx == -1) return "";
        int end = description.indexOf(')', idx);
        return end == -1 ? "" : description.substring(idx + 1, end);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readAnswers(OnboardingSession session) {
        try {
            return new LinkedHashMap<>(objectMapper.readValue(session.getAnswersJson(), new TypeReference<Map<String, Object>>() {
            }));
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeAnswers(Map<String, Object> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize answers", e);
        }
    }
}

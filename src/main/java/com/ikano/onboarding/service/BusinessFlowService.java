package com.ikano.onboarding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikano.onboarding.config.BusinessFlowConfig;
import com.ikano.onboarding.config.FieldDefinition;
import com.ikano.onboarding.config.StepDefinition;
import com.ikano.onboarding.domain.*;
import com.ikano.onboarding.dto.*;
import com.ikano.onboarding.repository.AuditEventRepository;
import com.ikano.onboarding.repository.OnboardingSessionRepository;
import com.ikano.onboarding.repository.SampleOnboardingUserDetailsRepository;
import com.ikano.onboarding.service.integration.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BusinessFlowService {

    private final BusinessFlowConfig flowConfig;
    private final OnboardingSessionRepository sessionRepository;
    private final AuditEventRepository auditEventRepository;
    private final SampleOnboardingUserDetailsRepository userDetailsRepository;
    private final RegistryMockService registryMockService;
    private final IdentityMockService identityMockService;
    private final PepSanctionsMockService pepSanctionsMockService;
    private final BusinessCreditMockService businessCreditMockService;
    private final BankAccountMockService bankAccountMockService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BusinessFlowService(BusinessFlowConfig flowConfig,
                               OnboardingSessionRepository sessionRepository,
                               AuditEventRepository auditEventRepository,
                               SampleOnboardingUserDetailsRepository userDetailsRepository,
                               RegistryMockService registryMockService,
                               IdentityMockService identityMockService,
                               PepSanctionsMockService pepSanctionsMockService,
                               BusinessCreditMockService businessCreditMockService,
                               BankAccountMockService bankAccountMockService) {
        this.flowConfig = flowConfig;
        this.sessionRepository = sessionRepository;
        this.auditEventRepository = auditEventRepository;
        this.userDetailsRepository = userDetailsRepository;
        this.registryMockService = registryMockService;
        this.identityMockService = identityMockService;
        this.pepSanctionsMockService = pepSanctionsMockService;
        this.businessCreditMockService = businessCreditMockService;
        this.bankAccountMockService = bankAccountMockService;
    }

    public com.ikano.onboarding.config.FlowDefinition getFlow(Country country) {
        return flowConfig.get(country);
    }

    @Transactional
    public CreateSessionResponse createSession(Country country, CustomerType customerType) {
        if (customerType != CustomerType.BUSINESS) {
            throw new IllegalArgumentException("Only BUSINESS customer type is supported by this API");
        }

        OnboardingSession session = new OnboardingSession(country, customerType);
        session = sessionRepository.save(session);
        saveUserDetailsSnapshot(session);
        recordAudit(session.getId(), null, null, null, "Business application started for " + country);
        return new CreateSessionResponse(session.getId(), flowConfig.get(country));
    }

    @Transactional
    public StepSubmitResponse submitStep(UUID sessionId, String stepKey, Map<String, Object> submittedAnswers) {
        OnboardingSession session = requireSession(sessionId);
        if (session.getCustomerType() != CustomerType.BUSINESS) {
            throw new IllegalArgumentException("Session is not BUSINESS type");
        }

        var flow = flowConfig.get(session.getCountry());
        StepDefinition step = flow.stepAt(session.getCurrentStepIndex());
        if (!step.key().equals(stepKey)) {
            throw new IllegalStateException("Step out of order: expected '" + step.key() + "' but got '" + stepKey + "'");
        }

        validateFields(step, submittedAnswers);

        Map<String, Object> answers = readAnswers(session);
        answers.putAll(submittedAnswers);
        session.setAnswersJson(writeAnswers(answers));

        Outcome outcome = null;
        String reason = null;
        String label = null;

        if (step.integrationType() != null) {
            IntegrationResult result = runIntegration(session.getCountry(), step, answers);
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

        return new StepSubmitResponse(step.key(), step.integrationType(), outcome, reason, label, nextIndex, nextIndex >= flow.steps().size());
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
                .map(AuditEvent::getOutcome)
                .toList();

        SessionStatus decision;
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

        return new SubmitResponse(decision, List.of(), auditTrail);
    }

    private IntegrationResult runIntegration(Country country, StepDefinition step, Map<String, Object> answers) {
        return switch (step.integrationType()) {
            case REGISTRY -> registryMockService.check(country, String.valueOf(answers.get(resolveRegistryField(country))));
            case IDENTITY -> identityMockService.check(country, String.valueOf(answers.get("representativeId")));
            case PEP_SANCTIONS -> pepSanctionsMockService.check("yes".equals(answers.get("uboPep")));
            case CREDIT_BUREAU -> businessCreditMockService.check(toDouble(answers.get("annualTurnover")), toDouble(answers.get("monthlyCosts")));
            case BANK_ACCOUNT -> bankAccountMockService.check(String.valueOf(answers.get("iban")));
        };
    }

    private String resolveRegistryField(Country country) {
        return switch (country) {
            case SWEDEN -> "organisationNumber";
            case SPAIN -> "companyNif";
            case POLAND -> "companyIdentifier";
        };
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
        auditEventRepository.save(new AuditEvent(sessionId, stepKey, integrationType, outcome, description));
    }

    private void saveUserDetailsSnapshot(OnboardingSession session) {
        SampleOnboardingUserDetails row = userDetailsRepository.findBySessionId(session.getId())
                .orElseGet(() -> new SampleOnboardingUserDetails(session.getId()));
        row.updateFromSession(session);
        userDetailsRepository.save(row);
    }

    private String extractReason(String description) {
        int idx = description.indexOf('(');
        if (idx == -1) return "";
        int end = description.indexOf(')', idx);
        return end == -1 ? "" : description.substring(idx + 1, end);
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

    private Map<String, Object> readAnswers(OnboardingSession session) {
        try {
            return new LinkedHashMap<>(objectMapper.readValue(session.getAnswersJson(), new TypeReference<Map<String, Object>>() {}));
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


package com.ikano.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists a single private-onboarding application's progress: selected
 * flow, submitted answers (as JSON, sanitized), current status and
 * timestamps. Answers are kept as a JSON blob rather than a rigid column
 * set, because the field set differs per country - this keeps the schema
 * stable while the flow config (see FlowConfig) evolves.
 */
@Entity
@Table(name = "onboarding_session")
public class OnboardingSession {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Column(name = "current_step_index", nullable = false)
    private int currentStepIndex = 0;

    /** Sanitized JSON snapshot of all answers submitted so far (sensitive values masked before storage). */
    @Column(name = "answers_json", columnDefinition = "TEXT")
    private String answersJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OnboardingSession() {
        // JPA
    }

    public OnboardingSession(Country country, CustomerType customerType) {
        this.country = country;
        this.customerType = customerType;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Country getCountry() {
        return country;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(int currentStepIndex) {
        this.currentStepIndex = currentStepIndex;
    }

    public String getAnswersJson() {
        return answersJson;
    }

    public void setAnswersJson(String answersJson) {
        this.answersJson = answersJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}


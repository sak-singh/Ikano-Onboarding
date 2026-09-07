package com.ikano.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores a denormalized snapshot of user-provided onboarding data.
 * This table is intended for easy support/audit inspection in addition
 * to the normalized onboarding_session and audit_event tables.
 */
@Entity
@Table(name = "sample_onboarding_user_details")
public class SampleOnboardingUserDetails {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "current_step_index", nullable = false)
    private int currentStepIndex;

    @Column(name = "answers_json", columnDefinition = "TEXT")
    private String answersJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SampleOnboardingUserDetails() {
        // JPA
    }

    public SampleOnboardingUserDetails(UUID sessionId) {
        this.sessionId = sessionId;
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

    public void updateFromSession(OnboardingSession session) {
        this.sessionId = session.getId();
        this.country = session.getCountry();
        this.customerType = session.getCustomerType();
        this.status = session.getStatus();
        this.currentStepIndex = session.getCurrentStepIndex();
        this.answersJson = session.getAnswersJson();
    }
}


package com.ikano.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit trail entry recorded for every step completion and
 * integration call. Descriptions must never contain raw sensitive values
 * (PESEL/DNI/personal id number) - callers are responsible for masking
 * before constructing this entity.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "step_key", length = 100)
    private String stepKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "integration_type", length = 30)
    private IntegrationType integrationType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Outcome outcome;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private Instant timestamp;

    protected AuditEvent() {
        // JPA
    }

    public AuditEvent(UUID sessionId, String stepKey, IntegrationType integrationType, Outcome outcome, String description) {
        this.sessionId = sessionId;
        this.stepKey = stepKey;
        this.integrationType = integrationType;
        this.outcome = outcome;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        this.timestamp = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getStepKey() {
        return stepKey;
    }

    public IntegrationType getIntegrationType() {
        return integrationType;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getDescription() {
        return description;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}

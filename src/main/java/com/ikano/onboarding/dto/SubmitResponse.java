package com.ikano.onboarding.dto;

import com.ikano.onboarding.domain.SessionStatus;

import java.time.Instant;
import java.util.List;

public record SubmitResponse(
        SessionStatus decision,
        List<String> reasons,
        List<AuditEntryView> auditTrail
) {
    public record AuditEntryView(Instant timestamp, String message) {
    }
}


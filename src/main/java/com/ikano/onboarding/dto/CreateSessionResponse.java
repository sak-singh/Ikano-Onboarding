package com.ikano.onboarding.dto;

import com.ikano.onboarding.config.FlowDefinition;

import java.util.UUID;

/**
 * Returned when a session is created. No auth token is issued - the
 * brief explicitly excludes a production authentication system. The
 * generated {@code sessionId} (UUID) is the sole identifier used for all
 * subsequent calls.
 */
public record CreateSessionResponse(
        UUID sessionId,
        FlowDefinition flow
) {
}

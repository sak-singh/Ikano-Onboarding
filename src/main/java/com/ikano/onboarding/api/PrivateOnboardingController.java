package com.ikano.onboarding.api;

import com.ikano.onboarding.config.FlowDefinition;
import com.ikano.onboarding.domain.Country;
import com.ikano.onboarding.dto.*;
import com.ikano.onboarding.service.PrivateFlowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API for the private-individual onboarding journey.
 * <p>
 * No authentication layer is implemented by design - the brief explicitly
 * excludes a production authentication system. Traceability instead comes
 * from the generated, non-guessable {@code sessionId} (UUID) used in every
 * URL, plus the audit trail recorded for each session (see AuditEvent).
 * <p>
 * Endpoints:
 * <ol>
 *   <li>{@code GET  /api/private/{country}/flow} - returns step/field config for a country</li>
 *   <li>{@code POST /api/private/sessions} - creates a session</li>
 *   <li>{@code POST /api/private/sessions/{id}/steps/{stepKey}} - submits step answers</li>
 *   <li>{@code GET  /api/private/sessions/{id}/review} - masked answers + integration results</li>
 *   <li>{@code POST /api/private/sessions/{id}/submit} - finalizes the decision</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/private")
public class PrivateOnboardingController {

    private final PrivateFlowService flowService;

    public PrivateOnboardingController(PrivateFlowService flowService) {
        this.flowService = flowService;
    }

    @GetMapping("/{country}/flow")
    public FlowDefinition getFlow(@PathVariable Country country) {
        return flowService.getFlow(country);
    }

    @PostMapping("/sessions")
    public ResponseEntity<CreateSessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        CreateSessionResponse response = flowService.createSession(request.country(), request.customerType());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sessions/{id}/steps/{stepKey}")
    public StepSubmitResponse submitStep(@PathVariable UUID id,
                                          @PathVariable String stepKey,
                                          @RequestBody StepSubmitRequest request) {
        return flowService.submitStep(id, stepKey, request.answers());
    }

    @GetMapping("/sessions/{id}/review")
    public ReviewResponse getReview(@PathVariable UUID id) {
        return flowService.getReview(id);
    }

    @PostMapping("/sessions/{id}/submit")
    public SubmitResponse submit(@PathVariable UUID id) {
        return flowService.submitApplication(id);
    }
}


package com.ikano.onboarding.api;

import com.ikano.onboarding.config.FlowDefinition;
import com.ikano.onboarding.domain.Country;
import com.ikano.onboarding.dto.*;
import com.ikano.onboarding.service.BusinessFlowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/business")
public class BusinessOnboardingController {

    private final BusinessFlowService flowService;

    public BusinessOnboardingController(BusinessFlowService flowService) {
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


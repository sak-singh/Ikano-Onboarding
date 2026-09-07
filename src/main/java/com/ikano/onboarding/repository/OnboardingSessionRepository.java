package com.ikano.onboarding.repository;

import com.ikano.onboarding.domain.OnboardingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OnboardingSessionRepository extends JpaRepository<OnboardingSession, UUID> {
}


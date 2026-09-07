package com.ikano.onboarding.repository;

import com.ikano.onboarding.domain.SampleOnboardingUserDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SampleOnboardingUserDetailsRepository extends JpaRepository<SampleOnboardingUserDetails, UUID> {
    Optional<SampleOnboardingUserDetails> findBySessionId(UUID sessionId);
}


package com.ikano.onboarding.repository;

import com.ikano.onboarding.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findBySessionIdOrderByTimestampAsc(UUID sessionId);
}


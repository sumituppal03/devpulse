package com.devpulse.shared.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LlmCallRepository extends JpaRepository<LlmCall, UUID> {
}
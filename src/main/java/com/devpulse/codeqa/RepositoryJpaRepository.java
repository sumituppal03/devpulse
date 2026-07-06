package com.devpulse.codeqa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RepositoryJpaRepository extends JpaRepository<Repository, UUID> {
    Optional<Repository> findByTenantIdAndId(UUID tenantId, UUID id);
}
package com.devpulse.codeqa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryJpaRepository extends JpaRepository<Repository, UUID> {

    Optional<Repository> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Returns all repos registered by a tenant.
     * Used by StandupController to know which repos to fetch commits from —
     * instead of requiring the caller to pass owner/repo manually on every request.
     */
    List<Repository> findByTenantId(UUID tenantId);
}

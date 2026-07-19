package com.devpulse.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantIntegrationRepository extends JpaRepository<TenantIntegration, UUID> {

    Optional<TenantIntegration> findByTenantIdAndIntegrationType(
            UUID tenantId, String integrationType);

    boolean existsByTenantIdAndIntegrationType(UUID tenantId, String integrationType);
}

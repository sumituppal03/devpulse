package com.devpulse.developer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeveloperRepository extends JpaRepository<Developer, UUID> {

    /** Always filtered by tenant — no developer from another tenant can leak through. */
    List<Developer> findByTenantId(UUID tenantId);
}
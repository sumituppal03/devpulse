package com.devpulse.developer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeveloperRepository extends JpaRepository<Developer, UUID> {

    List<Developer> findByTenantId(UUID tenantId);
}
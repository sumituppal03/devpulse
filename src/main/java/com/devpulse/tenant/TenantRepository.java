package com.devpulse.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /** The fast, indexed lookup used during authentication. */
    Optional<Tenant> findByKeyId(String keyId);
}
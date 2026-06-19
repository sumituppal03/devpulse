package com.devpulse.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "key_id", nullable = false, unique = true)
    private String keyId;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(nullable = false)
    private String plan = "FREE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Tenant create(String name, String keyId, String apiKeyHash) {
        Tenant tenant = new Tenant();
        tenant.name = name;
        tenant.keyId = keyId;
        tenant.apiKeyHash = apiKeyHash;
        tenant.plan = "FREE";
        tenant.createdAt = Instant.now();
        return tenant;
    }
}
package com.devpulse.integrations;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integrations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantIntegration {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // "SLACK" or "LINEAR"
    @Column(name = "integration_type", nullable = false)
    private String integrationType;

    // JSON string - different shape per integration type
    // SLACK:  {"webhookUrl": "https://hooks.slack.com/services/..."}
    // LINEAR: {"apiKey": "lin_api_..."}
    @Column(nullable = false, columnDefinition = "TEXT")
    private String config;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TenantIntegration create(UUID tenantId, String integrationType, String config) {
        TenantIntegration integration = new TenantIntegration();
        integration.tenantId = tenantId;
        integration.integrationType = integrationType;
        integration.config = config;
        integration.enabled = true;
        integration.createdAt = Instant.now();
        integration.updatedAt = Instant.now();
        return integration;
    }

    public void updateConfig(String newConfig) {
        this.config = newConfig;
        this.updatedAt = Instant.now();
    }
}

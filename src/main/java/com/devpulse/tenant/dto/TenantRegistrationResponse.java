package com.devpulse.tenant.dto;

import java.util.UUID;

public record TenantRegistrationResponse(
        UUID tenantId,
        String name,
        String apiKey,
        String warning
) {
    public static TenantRegistrationResponse of(UUID tenantId, String name, String plaintextApiKey) {
        return new TenantRegistrationResponse(
                tenantId,
                name,
                plaintextApiKey,
                "Save this API key now. It will not be shown again."
        );
    }
}
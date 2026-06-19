package com.devpulse.tenant.dto;

import java.util.UUID;

public record TenantProfileResponse(
        UUID tenantId,
        String name,
        String plan
) {}
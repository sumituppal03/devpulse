package com.devpulse.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What the client sends to register a new tenant.
 *
 * Why a separate class from the Tenant entity? Because the entity has fields
 * (id, apiKeyHash, createdAt) that the client should never set directly.
 * Mixing entities with request bodies is how accidental mass-assignment
 * vulnerabilities happen — a client could otherwise send {"plan": "ENTERPRISE"}
 * and grant themselves a free upgrade. DTOs are the boundary that prevents this.
 */
public record TenantRegistrationRequest(

        @NotBlank(message = "Company name is required")
        @Size(min = 2, max = 255, message = "Company name must be between 2 and 255 characters")
        String name

) {}


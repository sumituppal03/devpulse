package com.devpulse.tenant;

import com.devpulse.tenant.dto.TenantProfileResponse;
import com.devpulse.tenant.dto.TenantRegistrationRequest;
import com.devpulse.tenant.dto.TenantRegistrationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping("/register")
    public ResponseEntity<TenantRegistrationResponse> register(@Valid @RequestBody TenantRegistrationRequest request) {
        TenantRegistrationResponse response = tenantService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Proves the API key actually authenticates. Requires a valid
     * "Authorization: Bearer dp_live_..." header — see ApiKeyAuthenticationFilter.
     */
    @GetMapping("/me")
    public ResponseEntity<TenantProfileResponse> me() {
        UUID tenantId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(tenantService.getProfile(tenantId));
    }
}
package com.devpulse.developer;

import com.devpulse.developer.dto.DeveloperRegistrationRequest;
import com.devpulse.developer.dto.DeveloperResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/developers")
@RequiredArgsConstructor
public class DeveloperController {

    private final DeveloperService developerService;

    /**
     * Registers a developer under the authenticated tenant.
     * Any developer at the company can call this with the tenant's shared API key.
     */
    @PostMapping
    public ResponseEntity<DeveloperResponse> register(
            @Valid @RequestBody DeveloperRegistrationRequest request) {
        UUID tenantId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        DeveloperResponse response = developerService.register(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all developers registered under the authenticated tenant.
     * Frontend dashboard uses this to populate the developer selector.
     */
    @GetMapping
    public ResponseEntity<List<DeveloperResponse>> list() {
        UUID tenantId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<DeveloperResponse> developers = developerService.listByTenant(tenantId);
        return ResponseEntity.ok(developers);
    }
}

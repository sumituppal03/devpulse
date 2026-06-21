package com.devpulse.developer;

import com.devpulse.developer.dto.DeveloperRegistrationRequest;
import com.devpulse.developer.dto.DeveloperResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/developers")
@RequiredArgsConstructor
public class DeveloperController {

    private final DeveloperService developerService;

    @PostMapping
    public ResponseEntity<DeveloperResponse> register(@Valid @RequestBody DeveloperRegistrationRequest request) {
        UUID tenantId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        DeveloperResponse response = developerService.register(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
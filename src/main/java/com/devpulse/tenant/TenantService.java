package com.devpulse.tenant;

import com.devpulse.tenant.dto.TenantProfileResponse;
import com.devpulse.tenant.dto.TenantRegistrationRequest;
import com.devpulse.tenant.dto.TenantRegistrationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final ApiKeyService apiKeyService;

    @Transactional
    public TenantRegistrationResponse register(TenantRegistrationRequest request) {
        ApiKeyService.GeneratedApiKey generated = apiKeyService.generate();
        String hashedSecret = apiKeyService.hashSecret(generated.keySecret());

        Tenant tenant = Tenant.create(request.name(), generated.keyId(), hashedSecret);
        Tenant saved = tenantRepository.save(tenant);

        return TenantRegistrationResponse.of(saved.getId(), saved.getName(), generated.fullPlaintextKey());
    }

    public TenantProfileResponse getProfile(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Authenticated tenant not found: " + tenantId));
        return new TenantProfileResponse(tenant.getId(), tenant.getName(), tenant.getPlan());
    }
}
package com.devpulse.developer;

import com.devpulse.developer.dto.DeveloperRegistrationRequest;
import com.devpulse.developer.dto.DeveloperResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeveloperService {

    private final DeveloperRepository developerRepository;

    public DeveloperResponse register(UUID tenantId, DeveloperRegistrationRequest request) {
        Developer developer = Developer.create(tenantId, request.githubUsername(), request.timezone());
        Developer saved = developerRepository.save(developer);
        return new DeveloperResponse(saved.getId(), saved.getGithubUsername(), saved.getTimezone());
    }

    /**
     * Fetches a developer AND verifies it belongs to the calling tenant.
     * This single check is the actual multi-tenant security boundary —
     * without it, any authenticated tenant could request data for ANY
     * developer ID, not just their own.
     */
    public Developer getOwnedByTenant(UUID developerId, UUID tenantId) {
        Developer developer = developerRepository.findById(developerId)
                .orElseThrow(() -> new TenantOwnershipException("Developer not found"));

        if (!developer.getTenantId().equals(tenantId)) {
            throw new TenantOwnershipException("Developer does not belong to this tenant");
        }

        return developer;
    }
}
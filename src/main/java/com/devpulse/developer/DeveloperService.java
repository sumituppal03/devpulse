package com.devpulse.developer;

import com.devpulse.developer.dto.DeveloperRegistrationRequest;
import com.devpulse.developer.dto.DeveloperResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
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
     * Returns all developers belonging to this tenant.
     * Used by the dashboard to populate the developer list.
     */
    public List<DeveloperResponse> listByTenant(UUID tenantId) {
        return developerRepository.findByTenantId(tenantId).stream()
                .map(d -> new DeveloperResponse(d.getId(), d.getGithubUsername(), d.getTimezone()))
                .toList();
    }

    /**
     * Fetches a developer and verifies it belongs to the calling tenant.
     * Returns 404 if the developer doesn't exist OR belongs to a different tenant —
     * never reveals whether a resource exists to an unauthorized caller.
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

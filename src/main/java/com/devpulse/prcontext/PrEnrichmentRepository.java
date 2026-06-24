package com.devpulse.prcontext;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PrEnrichmentRepository extends JpaRepository<PrEnrichment, UUID> {
    boolean existsByGithubOwnerAndGithubRepoAndPrNumber(String owner, String repo, int prNumber);
}
package com.devpulse.standup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StandupRepository extends JpaRepository<Standup, UUID> {

    Optional<Standup> findByDeveloperIdAndStandupDate(UUID developerId, LocalDate standupDate);

    /**
     * Returns standup history for a developer within a date range.
     * Used by the history endpoint — frontend dashboard shows the last N days.
     */
    List<Standup> findByDeveloperIdAndStandupDateBetweenOrderByStandupDateDesc(
            UUID developerId, LocalDate from, LocalDate to);

    /**
     * Returns all standups for a tenant — used by admin dashboard.
     */
    List<Standup> findByTenantIdOrderByStandupDateDesc(UUID tenantId);
}

package com.devpulse.standup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface StandupRepository extends JpaRepository<Standup, UUID> {
    Optional<Standup> findByDeveloperIdAndStandupDate(UUID developerId, LocalDate standupDate);
}
package com.devpulse.standup;

import java.time.LocalDate;
import java.util.UUID;

public record StandupHistoryResponse(
        UUID standupId,
        LocalDate date,
        String content,
        boolean finalized,
        Integer editDistance,
        int commitsUsed
) {}

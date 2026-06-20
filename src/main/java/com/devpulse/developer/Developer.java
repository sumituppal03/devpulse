package com.devpulse.developer;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "developers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Developer {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "github_username", nullable = false)
    private String githubUsername;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Developer create(UUID tenantId, String githubUsername, String timezone) {
        Developer developer = new Developer();
        developer.tenantId = tenantId;
        developer.githubUsername = githubUsername;
        developer.timezone = (timezone != null) ? timezone : "UTC";
        developer.createdAt = Instant.now();
        return developer;
    }
}
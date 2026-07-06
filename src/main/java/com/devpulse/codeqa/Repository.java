package com.devpulse.codeqa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repositories")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Repository {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "github_owner", nullable = false)
    private String githubOwner;

    @Column(name = "github_repo", nullable = false)
    private String githubRepo;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch = "main";

    @Column(name = "index_status", nullable = false)
    private String indexStatus = "PENDING";

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Repository create(UUID tenantId, String owner, String repo, String branch) {
        Repository r = new Repository();
        r.tenantId = tenantId;
        r.githubOwner = owner;
        r.githubRepo = repo;
        r.defaultBranch = (branch != null) ? branch : "main";
        r.indexStatus = "PENDING";
        r.createdAt = Instant.now();
        return r;
    }
}
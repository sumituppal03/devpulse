package com.devpulse.prcontext;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pr_enrichments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrEnrichment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "github_owner", nullable = false)
    private String githubOwner;

    @Column(name = "github_repo", nullable = false)
    private String githubRepo;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "context_comment", nullable = false, columnDefinition = "TEXT")
    private String contextComment;

    @Column(name = "github_comment_id")
    private Long githubCommentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PrEnrichment create(String owner, String repo, int prNumber, String comment, Long commentId) {
        PrEnrichment e = new PrEnrichment();
        e.githubOwner = owner;
        e.githubRepo = repo;
        e.prNumber = prNumber;
        e.contextComment = comment;
        e.githubCommentId = commentId;
        e.createdAt = Instant.now();
        return e;
    }
}
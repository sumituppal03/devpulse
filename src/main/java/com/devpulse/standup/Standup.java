package com.devpulse.standup;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "standups")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Standup {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "developer_id", nullable = false)
    private UUID developerId;

    @Column(name = "standup_date", nullable = false)
    private LocalDate standupDate;

    @Column(name = "generated_content", nullable = false, columnDefinition = "TEXT")
    private String generatedContent;

    // The developer's edited version. NULL until PUT /standup/{id}/finalize is called.
    // This is what gets posted to Slack. Keeping both versions lets us
    // compute edit_distance as a product quality metric.
    @Column(name = "final_content", columnDefinition = "TEXT")
    private String finalContent;

    // How many characters changed between generated and final content.
    // Low value = AI was accurate. High value = prompts need improvement.
    @Column(name = "edit_distance")
    private Integer editDistance;

    @Column(name = "commits_used", nullable = false)
    private int commitsUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Standup create(UUID tenantId, UUID developerId, LocalDate standupDate,
                                  String generatedContent, int commitsUsed) {
        Standup standup = new Standup();
        standup.tenantId = tenantId;
        standup.developerId = developerId;
        standup.standupDate = standupDate;
        standup.generatedContent = generatedContent;
        standup.commitsUsed = commitsUsed;
        standup.createdAt = Instant.now();
        return standup;
    }

    public void updateContent(String generatedContent, int commitsUsed) {
        this.generatedContent = generatedContent;
        this.commitsUsed = commitsUsed;
        this.finalContent = null;
        this.editDistance = null;
    }
}
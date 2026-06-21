package com.devpulse.shared.ai;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_calls")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmCall {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "developer_id")
    private UUID developerId;

    @Column(nullable = false)
    private String feature;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static LlmCall create(UUID tenantId, UUID developerId, String feature, String modelName,
                                  Integer promptTokens, Integer completionTokens, long latencyMs) {
        LlmCall call = new LlmCall();
        call.tenantId = tenantId;
        call.developerId = developerId;
        call.feature = feature;
        call.modelName = modelName;
        call.promptTokens = promptTokens;
        call.completionTokens = completionTokens;
        call.latencyMs = latencyMs;
        call.createdAt = Instant.now();
        return call;
    }
}
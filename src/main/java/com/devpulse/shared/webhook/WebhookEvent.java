package com.devpulse.shared.webhook;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String source;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private boolean processed = false;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public static WebhookEvent create(String source, String eventType, String payload) {
        WebhookEvent event = new WebhookEvent();
        event.source = source;
        event.eventType = eventType;
        event.payload = payload;
        event.processed = false;
        event.receivedAt = Instant.now();
        return event;
    }
}
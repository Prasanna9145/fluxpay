package com.prasanna.fluxpay.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_due", columnList = "published_at, next_attempt_at"))
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 120) private String topic;
    @Column(name = "message_key", nullable = false, length = 120) private String messageKey;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private LocalDateTime nextAttemptAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Column(name = "last_error", length = 1000) private String lastError;

    protected OutboxEvent() {}
    public OutboxEvent(String topic, String messageKey, String payload) {
        this.topic = topic; this.messageKey = messageKey; this.payload = payload;
        this.attempts = 0; this.createdAt = LocalDateTime.now(); this.nextAttemptAt = this.createdAt;
    }
    public UUID getId() { return id; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void markPublished() { this.publishedAt = LocalDateTime.now(); this.lastError = null; }
    public void markFailed(String error) {
        this.attempts++;
        int delay = Math.min(300, 2 * (1 << Math.min(this.attempts, 7)));
        this.nextAttemptAt = LocalDateTime.now().plusSeconds(delay);
        this.lastError = error == null ? "Unknown Kafka publishing error" : error.substring(0, Math.min(1000, error.length()));
    }
}

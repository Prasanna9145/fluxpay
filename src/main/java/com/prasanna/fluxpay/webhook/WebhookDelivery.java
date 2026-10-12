package com.prasanna.fluxpay.webhook;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries", indexes = @Index(name = "idx_webhook_delivery_due", columnList = "status, next_attempt_at"))
public class WebhookDelivery {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "endpoint_id", nullable = false) private WebhookEndpoint endpoint;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private LocalDateTime nextAttemptAt;
    @Column(name = "response_status") private Integer responseStatus;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected WebhookDelivery() {}
    public WebhookDelivery(WebhookEndpoint endpoint, String eventType, UUID paymentId, String payload) {
        this.endpoint = endpoint; this.eventType = eventType; this.paymentId = paymentId; this.payload = payload;
        this.status = "PENDING"; this.attempts = 0; this.nextAttemptAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now(); this.updatedAt = this.createdAt;
    }
    public UUID getId() { return id; }
    public WebhookEndpoint getEndpoint() { return endpoint; }
    public String getEventType() { return eventType; }
    public UUID getPaymentId() { return paymentId; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getLastError() { return lastError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void markDelivered(int statusCode) { this.status = "DELIVERED"; this.responseStatus = statusCode; this.lastError = null; this.updatedAt = LocalDateTime.now(); }
    public void markFailed(String error, int maxAttempts, int baseSeconds) {
        this.attempts++; this.lastError = error == null ? "Delivery failed" : error.substring(0, Math.min(1000, error.length()));
        if (this.attempts >= maxAttempts) { this.status = "FAILED"; }
        else { this.status = "RETRYING"; int delay = Math.min(3600, baseSeconds * (1 << Math.min(this.attempts - 1, 10))); this.nextAttemptAt = LocalDateTime.now().plusSeconds(delay); }
        this.updatedAt = LocalDateTime.now();
    }
}

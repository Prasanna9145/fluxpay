package com.prasanna.fluxpay.webhook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.prasanna.fluxpay.model.AppUser;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints", indexes = @Index(name = "idx_webhook_user", columnList = "user_id, active"))
public class WebhookEndpoint {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) @JsonIgnore private AppUser user;
    @Column(name = "target_url", nullable = false, length = 2048) private String targetUrl;
    @Column(name = "signing_secret", nullable = false, length = 100) private String signingSecret;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected WebhookEndpoint() {}
    public WebhookEndpoint(AppUser user, String targetUrl, String signingSecret) {
        this.user = user; this.targetUrl = targetUrl; this.signingSecret = signingSecret;
        this.active = true; this.createdAt = LocalDateTime.now();
    }
    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public String getTargetUrl() { return targetUrl; }
    public String getSigningSecret() { return signingSecret; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

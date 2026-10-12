package com.prasanna.fluxpay.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_events", indexes = @Index(name = "idx_audit_actor_created", columnList = "actor, created_at"))
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 50) private String actor;
    @Column(nullable = false, length = 80) private String action;
    @Column(name = "resource_type", nullable = false, length = 60) private String resourceType;
    @Column(name = "resource_id", length = 100) private String resourceId;
    @Column(length = 1000) private String details;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected AuditEvent() {}
    public AuditEvent(String actor, String action, String resourceType, String resourceId, String details) {
        this.actor = actor; this.action = action; this.resourceType = resourceType;
        this.resourceId = resourceId; this.details = details; this.createdAt = LocalDateTime.now();
    }
    public UUID getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

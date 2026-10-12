package com.prasanna.fluxpay.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AuditService {
    private final AuditEventRepository repository;
    public AuditService(AuditEventRepository repository) { this.repository = repository; }

    @Transactional
    public void record(String actor, String action, String type, String id, String details) {
        String safeActor = actor == null || actor.isBlank() ? "system" : actor.substring(0, Math.min(50, actor.length()));
        String safeDetails = details == null ? null : details.substring(0, Math.min(1000, details.length()));
        repository.save(new AuditEvent(safeActor, action, type, id, safeDetails));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> forActor(String actor) {
        return repository.findTop100ByActorOrderByCreatedAtDesc(actor);
    }
}

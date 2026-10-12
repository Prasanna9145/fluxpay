package com.prasanna.fluxpay.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(LocalDateTime now);
}

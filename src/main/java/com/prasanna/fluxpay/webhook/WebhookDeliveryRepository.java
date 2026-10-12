package com.prasanna.fluxpay.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    List<WebhookDelivery> findTop100ByEndpoint_User_UsernameOrderByCreatedAtDesc(String username);
    List<WebhookDelivery> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(List<String> statuses, LocalDateTime now);
}

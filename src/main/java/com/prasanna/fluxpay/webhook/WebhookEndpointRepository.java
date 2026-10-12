package com.prasanna.fluxpay.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByUser_UsernameAndActiveTrue(String username);
    List<WebhookEndpoint> findByUser_UsernameOrderByCreatedAtDesc(String username);
}

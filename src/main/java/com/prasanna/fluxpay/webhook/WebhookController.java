package com.prasanna.fluxpay.webhook;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
    private final WebhookService webhookService;
    public WebhookController(WebhookService webhookService) { this.webhookService = webhookService; }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookService.WebhookCreated create(@Valid @RequestBody CreateWebhookRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return webhookService.register(jwt.getSubject(), request.targetUrl());
    }
    @GetMapping
    public List<WebhookService.WebhookSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return webhookService.list(jwt.getSubject());
    }
    @GetMapping("/deliveries")
    public List<WebhookService.WebhookDeliverySummary> deliveries(@AuthenticationPrincipal Jwt jwt) {
        return webhookService.deliveryHistory(jwt.getSubject());
    }
    public record CreateWebhookRequest(@NotBlank @Size(max = 2048) String targetUrl) {}
}

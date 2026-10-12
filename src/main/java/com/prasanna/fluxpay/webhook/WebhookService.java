package com.prasanna.fluxpay.webhook;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.prasanna.fluxpay.audit.AuditService;
import com.prasanna.fluxpay.model.AppUser;
import com.prasanna.fluxpay.model.Payment;
import com.prasanna.fluxpay.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final JsonMapper objectMapper;
    private final AuditService auditService;
    private final int maxAttempts;
    private final int retryBaseSeconds;
    // A webhook target must not be allowed to hang the scheduled worker or redirect
    // the client to a different host after the registration-time URL check.
    private final RestClient restClient = createRestClient();

    private static RestClient createRestClient() {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }

    public WebhookService(WebhookEndpointRepository endpointRepository, WebhookDeliveryRepository deliveryRepository,
            UserRepository userRepository, JsonMapper objectMapper, AuditService auditService,
            @Value("${app.webhooks.max-attempts:6}") int maxAttempts,
            @Value("${app.webhooks.retry-base-seconds:5}") int retryBaseSeconds) {
        this.endpointRepository = endpointRepository; this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository; this.objectMapper = objectMapper; this.auditService = auditService;
        this.maxAttempts = Math.max(1, maxAttempts); this.retryBaseSeconds = Math.max(1, retryBaseSeconds);
    }

    @Transactional
    public WebhookCreated register(String username, String targetUrl) {
        URI uri;
        try { uri = URI.create(targetUrl.trim()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook URL is invalid"); }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) || uri.getUserInfo() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook URL must be a valid HTTP(S) URL without embedded credentials");
        }
        try {
            InetAddress resolved = InetAddress.getByName(host);
            boolean loopbackHost = host.equalsIgnoreCase("localhost") || resolved.isLoopbackAddress();
            if (!loopbackHost && (resolved.isAnyLocalAddress() || resolved.isLinkLocalAddress() || resolved.isSiteLocalAddress() || resolved.isMulticastAddress())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook URL cannot target a private or link-local network address");
            }
            if ("http".equalsIgnoreCase(scheme) && !loopbackHost) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use HTTPS for non-local webhook endpoints");
            }
        } catch (java.net.UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook hostname could not be resolved");
        }
        AppUser user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String secret = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        WebhookEndpoint saved = endpointRepository.save(new WebhookEndpoint(user, uri.toString(), secret));
        auditService.record(username, "WEBHOOK_CREATED", "webhook", saved.getId().toString(), "Webhook endpoint registered");
        return new WebhookCreated(saved.getId(), saved.getTargetUrl(), secret, saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<WebhookSummary> list(String username) {
        return endpointRepository.findByUser_UsernameOrderByCreatedAtDesc(username).stream()
            .map(e -> new WebhookSummary(e.getId(), e.getTargetUrl(), e.isActive(), e.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliverySummary> deliveryHistory(String username) {
        return deliveryRepository.findTop100ByEndpoint_User_UsernameOrderByCreatedAtDesc(username).stream()
            .map(d -> new WebhookDeliverySummary(d.getId(), d.getEndpoint().getId(), d.getEndpoint().getTargetUrl(),
                d.getEventType(), d.getPaymentId(), d.getStatus(), d.getAttempts(), d.getResponseStatus(),
                d.getLastError(), d.getCreatedAt(), d.getUpdatedAt())).toList();
    }

    @Transactional
    public void enqueuePaymentEvent(Payment payment, String eventType, String actor) {
        List<WebhookEndpoint> endpoints = endpointRepository.findByUser_UsernameAndActiveTrue(actor);
        if (endpoints.isEmpty()) return;
        Map<String, Object> payload = Map.of(
            "id", UUID.randomUUID().toString(), "type", eventType,
            "createdAt", LocalDateTime.now().toString(),
            "data", Map.of("paymentId", payment.getId().toString(), "amount", payment.getAmount(),
                "currency", payment.getCurrency(), "status", payment.getStatus().name())
        );
        final String json;
        try { json = objectMapper.writeValueAsString(payload); }
        catch (JacksonException e) { throw new IllegalArgumentException("Webhook event payload could not be serialized", e); }
        for (WebhookEndpoint endpoint : endpoints) deliveryRepository.save(new WebhookDelivery(endpoint, eventType, payment.getId(), json));
    }

    @Scheduled(fixedDelayString = "${app.webhooks.poll-interval-ms:15000}")
    @Transactional
    public void deliverDueWebhooks() {
        List<WebhookDelivery> due = deliveryRepository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List.of("PENDING", "RETRYING"), LocalDateTime.now());
        for (WebhookDelivery delivery : due) {
            try {
                String signature = sign(delivery.getPayload(), delivery.getEndpoint().getSigningSecret());
                var response = restClient.post().uri(delivery.getEndpoint().getTargetUrl())
                    .header("Content-Type", "application/json")
                    .header("X-FluxPay-Event", delivery.getEventType())
                    .header("X-FluxPay-Signature", "sha256=" + signature)
                    .body(delivery.getPayload()).retrieve().toBodilessEntity();
                delivery.markDelivered(response.getStatusCode().value());
            } catch (Exception exception) {
                delivery.markFailed(exception.getMessage(), maxAttempts, retryBaseSeconds);
                log.warn("Webhook delivery {} failed; status is {}", delivery.getId(), delivery.getStatus());
            }
        }
    }

    private String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    public record WebhookCreated(UUID id, String targetUrl, String signingSecret, LocalDateTime createdAt) {}
    public record WebhookSummary(UUID id, String targetUrl, boolean active, LocalDateTime createdAt) {}
    public record WebhookDeliverySummary(UUID id, UUID endpointId, String targetUrl, String eventType, UUID paymentId,
        String status, int attempts, Integer responseStatus, String lastError, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}

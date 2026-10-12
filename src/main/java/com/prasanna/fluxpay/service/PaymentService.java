package com.prasanna.fluxpay.service;

import com.prasanna.fluxpay.audit.AuditService;
import com.prasanna.fluxpay.event.PaymentCreatedEvent;
import com.prasanna.fluxpay.ledger.LedgerService;
import com.prasanna.fluxpay.model.AppUser;
import com.prasanna.fluxpay.model.Payment;
import com.prasanna.fluxpay.model.PaymentStatus;
import com.prasanna.fluxpay.outbox.OutboxService;
import com.prasanna.fluxpay.provider.SimulatedPaymentProvider;
import com.prasanna.fluxpay.repository.PaymentRepository;
import com.prasanna.fluxpay.repository.UserRepository;
import com.prasanna.fluxpay.util.RequestFingerprint;
import com.prasanna.fluxpay.webhook.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final OutboxService outboxService;
    private final LedgerService ledgerService;
    private final WebhookService webhookService;
    private final AuditService auditService;
    private final SimulatedPaymentProvider paymentProvider;

    public PaymentService(PaymentRepository paymentRepository, UserRepository userRepository,
                          OutboxService outboxService, LedgerService ledgerService,
                          WebhookService webhookService, AuditService auditService,
                          SimulatedPaymentProvider paymentProvider) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.outboxService = outboxService;
        this.ledgerService = ledgerService;
        this.webhookService = webhookService;
        this.auditService = auditService;
        this.paymentProvider = paymentProvider;
    }

    @Transactional
    public Payment createPayment(BigDecimal amount, String currency, String customerEmail,
                                 String idempotencyKey, String username) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Idempotency-Key header is required and must be at most 100 characters");
        }
        if (amount == null || amount.scale() > 2 || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive and have at most two decimal places");
        }
        String normalizedCurrency = currency == null ? null : currency.toUpperCase(java.util.Locale.ROOT);
        String normalizedEmail = customerEmail == null ? null : customerEmail.trim();
        AppUser user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String fingerprint = RequestFingerprint.payment(amount, normalizedCurrency, normalizedEmail);

        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return validateIdempotentReplay(existing.get(), user, amount, normalizedCurrency, normalizedEmail, fingerprint);

        UUID paymentId = UUID.randomUUID();
        int inserted = paymentRepository.insertPaymentIfAbsent(paymentId, amount, normalizedCurrency,
            normalizedEmail, idempotencyKey, fingerprint, user.getId());
        Payment payment = paymentRepository.findByIdempotencyKey(idempotencyKey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Payment could not be created"));
        if (inserted == 0) return validateIdempotentReplay(payment, user, amount, normalizedCurrency, normalizedEmail, fingerprint);

        PaymentCreatedEvent event = new PaymentCreatedEvent(payment.getId(), user.getId(), payment.getAmount(),
            payment.getCurrency(), payment.getCustomerEmail(), payment.getCreatedAt());
        outboxService.enqueue("payment.created", payment.getId().toString(), event);
        webhookService.enqueuePaymentEvent(payment, "payment.created", username);
        auditService.record(username, "PAYMENT_CREATED", "payment", payment.getId().toString(),
            "Created payment for " + payment.getAmount().toPlainString() + " " + payment.getCurrency());
        return payment;
    }

    private Payment validateIdempotentReplay(Payment payment, AppUser user, BigDecimal amount,
                                               String currency, String customerEmail, String fingerprint) {
        if (payment.getUser() == null || !payment.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key is already in use");
        }
        boolean sameFingerprint = payment.getRequestFingerprint() != null && payment.getRequestFingerprint().equals(fingerprint);
        boolean sameFields = payment.getAmount().compareTo(amount) == 0
            && payment.getCurrency().equals(currency)
            && payment.getCustomerEmail().equalsIgnoreCase(customerEmail);
        if (!sameFields || (payment.getRequestFingerprint() != null && !sameFingerprint)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This Idempotency-Key was already used with a different request body");
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public List<Payment> getPayments(String username) {
        return paymentRepository.findByUser_Username(username);
    }

    @Transactional(readOnly = true)
    public Payment getPaymentById(UUID id, String username) {
        return paymentRepository.findByIdAndUser_Username(id, username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    @Transactional
    public Payment updatePaymentStatus(UUID id, PaymentStatus newStatus, String username) {
        Payment payment = paymentRepository.findByIdAndUser_Username(id, username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        return applyStatusChange(payment, newStatus, username);
    }

    private Payment applyStatusChange(Payment payment, PaymentStatus newStatus, String username) {
        if (newStatus == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        PaymentStatus current = payment.getStatus();
        if (current == newStatus) return payment;
        if (isFinal(current)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment is already in a final state");
        PaymentStatusTransitions.requireAllowed(current, newStatus);
        payment.setStatus(newStatus);
        Payment saved = paymentRepository.save(payment);
        if (newStatus == PaymentStatus.COMPLETED) ledgerService.postCompletedPayment(saved);
        Map<String, Object> statusEvent = new LinkedHashMap<>();
        statusEvent.put("paymentId", saved.getId().toString());
        statusEvent.put("status", saved.getStatus().name());
        statusEvent.put("amount", saved.getAmount());
        statusEvent.put("currency", saved.getCurrency());
        statusEvent.put("updatedBy", username);
        statusEvent.put("occurredAt", java.time.LocalDateTime.now().toString());
        outboxService.enqueue("payment.status_changed", saved.getId().toString(), statusEvent);
        webhookService.enqueuePaymentEvent(saved, "payment.status_changed", username);
        auditService.record(username, "PAYMENT_STATUS_CHANGED", "payment", saved.getId().toString(), current + " -> " + newStatus);
        return saved;
    }

    @Transactional
    public ReconciliationResult reconcilePayments(String username) {
        List<Payment> candidates = paymentRepository.findByUser_UsernameAndStatusIn(username,
            List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING));
        int completed = 0, failed = 0;
        for (Payment payment : candidates) {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.PROCESSING);
                paymentRepository.saveAndFlush(payment);
            }
            PaymentStatus result = paymentProvider.process(payment);
            payment.setStatus(result);
            paymentRepository.save(payment);
            if (result == PaymentStatus.COMPLETED) {
                ledgerService.postCompletedPayment(payment);
                completed++;
            } else {
                failed++;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("paymentId", payment.getId().toString()); event.put("status", result.name());
            event.put("amount", payment.getAmount()); event.put("currency", payment.getCurrency());
            event.put("provider", "FLUXPAY_SIMULATED_PROVIDER");
            event.put("occurredAt", java.time.LocalDateTime.now().toString());
            outboxService.enqueue("payment.status_changed", payment.getId().toString(), event);
            webhookService.enqueuePaymentEvent(payment, "payment.status_changed", username);
            auditService.record(username, "PAYMENT_RECONCILED", "payment", payment.getId().toString(), "Simulated provider result: " + result);
        }
        return new ReconciliationResult(candidates.size(), completed, failed);
    }

    private boolean isFinal(PaymentStatus status) {
        return status == PaymentStatus.COMPLETED || status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED;
    }

    public record ReconciliationResult(int processed, int completed, int failed) {}
}

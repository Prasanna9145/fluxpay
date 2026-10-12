package com.prasanna.fluxpay.controller;

import com.prasanna.fluxpay.model.Payment;
import com.prasanna.fluxpay.model.PaymentStatus;
import com.prasanna.fluxpay.ratelimit.RateLimitService;
import com.prasanna.fluxpay.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final RateLimitService rateLimitService;
    public PaymentController(PaymentService paymentService, RateLimitService rateLimitService) {
        this.paymentService = paymentService; this.rateLimitService = rateLimitService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payment createPayment(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        rateLimitService.checkPaymentCreate(httpRequest);
        return paymentService.createPayment(request.amount(), request.currency(), request.customerEmail(),
            idempotencyKey, jwt.getSubject());
    }

    @GetMapping
    public List<Payment> getPayments(@AuthenticationPrincipal Jwt jwt) {
        return paymentService.getPayments(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public Payment getPaymentById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return paymentService.getPaymentById(id, jwt.getSubject());
    }

    @PatchMapping("/{id}/status")
    public Payment updatePaymentStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return paymentService.updatePaymentStatus(id, request.status(), jwt.getSubject());
    }

    public record CreatePaymentRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        @Digits(integer = 17, fraction = 2, message = "amount supports at most 2 decimal places")
        BigDecimal amount,
        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter code") String currency,
        @NotBlank(message = "customer email is required")
        @Email(message = "customer email must be valid") String customerEmail) {}

    public record UpdateStatusRequest(@NotNull(message = "status is required") PaymentStatus status) {}
}

package com.prasanna.fluxpay.reconciliation;

import com.prasanna.fluxpay.service.PaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {
    private final PaymentService paymentService;
    public ReconciliationController(PaymentService paymentService) { this.paymentService = paymentService; }
    @PostMapping("/run")
    public PaymentService.ReconciliationResult run(@AuthenticationPrincipal Jwt jwt) {
        return paymentService.reconcilePayments(jwt.getSubject());
    }
}

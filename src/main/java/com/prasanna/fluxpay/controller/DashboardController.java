package com.prasanna.fluxpay.controller;

import com.prasanna.fluxpay.model.Payment;
import com.prasanna.fluxpay.model.PaymentStatus;
import com.prasanna.fluxpay.service.PaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final PaymentService paymentService;
    public DashboardController(PaymentService paymentService) { this.paymentService = paymentService; }
    @GetMapping("/summary")
    public DashboardSummary summary(@AuthenticationPrincipal Jwt jwt) {
        List<Payment> payments = paymentService.getPayments(jwt.getSubject());
        Map<String, BigDecimal> completedVolumeByCurrency = new LinkedHashMap<>();
        Map<PaymentStatus, Long> countByStatus = new EnumMap<>(PaymentStatus.class);
        for (PaymentStatus status : PaymentStatus.values()) countByStatus.put(status, 0L);
        for (Payment payment : payments) {
            countByStatus.compute(payment.getStatus(), (key, count) -> count == null ? 1L : count + 1L);
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                completedVolumeByCurrency.merge(payment.getCurrency(), payment.getAmount(), BigDecimal::add);
            }
        }
        return new DashboardSummary(payments.size(), countByStatus, completedVolumeByCurrency);
    }
    public record DashboardSummary(int totalPayments, Map<PaymentStatus, Long> countByStatus,
                                   Map<String, BigDecimal> completedVolumeByCurrency) {}
}

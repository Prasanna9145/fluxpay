package com.prasanna.fluxpay.ledger;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {
    private final LedgerService ledgerService;
    public LedgerController(LedgerService ledgerService) { this.ledgerService = ledgerService; }
    @GetMapping
    public List<LedgerEntry> myEntries(@AuthenticationPrincipal Jwt jwt) { return ledgerService.forUser(jwt.getSubject()); }
    @GetMapping("/payments/{paymentId}")
    public List<LedgerEntry> forPayment(@PathVariable UUID paymentId, @AuthenticationPrincipal Jwt jwt) {
        return ledgerService.forPayment(paymentId, jwt.getSubject());
    }
}

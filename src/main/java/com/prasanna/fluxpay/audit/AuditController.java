package com.prasanna.fluxpay.audit;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditService auditService;
    public AuditController(AuditService auditService) { this.auditService = auditService; }
    @GetMapping
    public List<AuditEvent> history(@AuthenticationPrincipal Jwt jwt) {
        return auditService.forActor(jwt.getSubject());
    }
}

package com.prasanna.fluxpay;

import com.prasanna.fluxpay.ledger.LedgerBalanceRules;
import com.prasanna.fluxpay.util.RequestFingerprint;
import com.prasanna.fluxpay.model.PaymentStatus;
import com.prasanna.fluxpay.service.PaymentStatusTransitions;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

/** These unit tests deliberately do not require a running database, Redis or Kafka broker. */
class FluxpayApplicationTests {
    @Test
    void fingerprintCanonicalizesMoneyCurrencyAndEmail() {
        assertEquals(
            RequestFingerprint.payment(new BigDecimal("10.0"), "inr", "Customer@example.com"),
            RequestFingerprint.payment(new BigDecimal("10.00"), "INR", "customer@example.com")
        );
    }

    @Test
    void fingerprintChangesWhenRequestPayloadChanges() {
        assertNotEquals(
            RequestFingerprint.payment(new BigDecimal("10.00"), "INR", "customer@example.com"),
            RequestFingerprint.payment(new BigDecimal("11.00"), "INR", "customer@example.com")
        );
    }

    @Test
    void paymentStateMachineAllowsOnlyExpectedTransitions() {
        assertTrue(PaymentStatusTransitions.isAllowed(PaymentStatus.PENDING, PaymentStatus.PROCESSING));
        assertTrue(PaymentStatusTransitions.isAllowed(PaymentStatus.PENDING, PaymentStatus.CANCELLED));
        assertTrue(PaymentStatusTransitions.isAllowed(PaymentStatus.PROCESSING, PaymentStatus.COMPLETED));
        assertTrue(PaymentStatusTransitions.isAllowed(PaymentStatus.PROCESSING, PaymentStatus.FAILED));
        assertFalse(PaymentStatusTransitions.isAllowed(PaymentStatus.PENDING, PaymentStatus.COMPLETED));
        assertFalse(PaymentStatusTransitions.isAllowed(PaymentStatus.COMPLETED, PaymentStatus.FAILED));
        assertThrows(ResponseStatusException.class,
            () -> PaymentStatusTransitions.requireAllowed(PaymentStatus.FAILED, PaymentStatus.COMPLETED));
    }

    @Test
    void ledgerRequiresEqualDebitsAndCredits() {
        assertTrue(LedgerBalanceRules.isBalanced(new BigDecimal("5.00"), new BigDecimal("5.0")));
        assertFalse(LedgerBalanceRules.isBalanced(new BigDecimal("5.00"), new BigDecimal("4.99")));
        assertThrows(IllegalStateException.class,
            () -> LedgerBalanceRules.requireBalanced(new BigDecimal("5.00"), new BigDecimal("4.99")));
    }
}

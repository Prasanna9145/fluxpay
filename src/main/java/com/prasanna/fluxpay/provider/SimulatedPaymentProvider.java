package com.prasanna.fluxpay.provider;

import com.prasanna.fluxpay.model.Payment;
import com.prasanna.fluxpay.model.PaymentStatus;
import org.springframework.stereotype.Component;

/** Deterministic local fake provider. Never moves money or calls a real payment network. */
@Component
public class SimulatedPaymentProvider {
    public PaymentStatus process(Payment payment) {
        // Stable demo behavior: 1 in 5 UUID buckets fail so both outcomes are testable.
        return Math.floorMod(payment.getId().getLeastSignificantBits(), 5) == 0
            ? PaymentStatus.FAILED : PaymentStatus.COMPLETED;
    }
}

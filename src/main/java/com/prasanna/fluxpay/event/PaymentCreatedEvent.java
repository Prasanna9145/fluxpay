package com.prasanna.fluxpay.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentCreatedEvent(
        UUID paymentId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String customerEmail,
        LocalDateTime createdAt
) {
}
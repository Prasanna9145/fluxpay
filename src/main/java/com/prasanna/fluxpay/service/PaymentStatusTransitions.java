package com.prasanna.fluxpay.service;

import com.prasanna.fluxpay.model.PaymentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Explicit state-machine rules for payment lifecycle changes. */
public final class PaymentStatusTransitions {
    private PaymentStatusTransitions() {}

    public static boolean isAllowed(PaymentStatus current, PaymentStatus next) {
        if (current == null || next == null) return false;
        if (current == next) return true;
        if (current == PaymentStatus.PENDING) {
            return next == PaymentStatus.PROCESSING || next == PaymentStatus.CANCELLED;
        }
        if (current == PaymentStatus.PROCESSING) {
            return next == PaymentStatus.COMPLETED || next == PaymentStatus.FAILED;
        }
        return false;
    }

    public static void requireAllowed(PaymentStatus current, PaymentStatus next) {
        if (!isAllowed(current, next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid payment status transition from " + current + " to " + next);
        }
    }
}

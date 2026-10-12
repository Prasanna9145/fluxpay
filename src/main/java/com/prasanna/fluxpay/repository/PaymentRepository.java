package com.prasanna.fluxpay.repository;

import com.prasanna.fluxpay.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByUser_Username(String username);

    Optional<Payment> findByIdAndUser_Username(
            UUID id,
            String username
    );

    @Modifying
    @Query(value = """
            INSERT INTO payments (
                id,
                amount,
                currency,
                status,
                customer_email,
                created_at,
                idempotency_key,
                request_fingerprint,
                user_id
            )
            VALUES (
                :id,
                :amount,
                :currency,
                'PENDING',
                :customerEmail,
                CURRENT_TIMESTAMP,
                :idempotencyKey,
                :requestFingerprint,
                :userId
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertPaymentIfAbsent(
            @Param("id") UUID id,
            @Param("amount") BigDecimal amount,
            @Param("currency") String currency,
            @Param("customerEmail") String customerEmail,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("userId") UUID userId
    );

    List<Payment> findByUser_UsernameAndStatusIn(String username, List<com.prasanna.fluxpay.model.PaymentStatus> statuses);
}

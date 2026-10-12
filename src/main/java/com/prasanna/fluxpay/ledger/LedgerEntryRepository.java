package com.prasanna.fluxpay.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    boolean existsByEntryKey(String entryKey);
    List<LedgerEntry> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
    List<LedgerEntry> findByPaymentIdInOrderByCreatedAtDesc(List<UUID> paymentIds);
}

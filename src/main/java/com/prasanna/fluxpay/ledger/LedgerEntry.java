package com.prasanna.fluxpay.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_ledger_payment", columnList = "payment_id"),
    @Index(name = "idx_ledger_created_at", columnList = "created_at")
})
public class LedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "entry_group_id", nullable = false) private UUID entryGroupId;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "entry_key", nullable = false, unique = true, length = 120) private String entryKey;
    @Column(name = "account_code", nullable = false, length = 60) private String accountCode;
    @Column(nullable = false, length = 8) private String direction;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected LedgerEntry() {}
    public LedgerEntry(UUID entryGroupId, UUID paymentId, String entryKey, String accountCode,
                       String direction, BigDecimal amount, String currency) {
        this.entryGroupId = entryGroupId; this.paymentId = paymentId; this.entryKey = entryKey;
        this.accountCode = accountCode; this.direction = direction; this.amount = amount;
        this.currency = currency; this.createdAt = LocalDateTime.now();
    }
    public UUID getId() { return id; }
    public UUID getEntryGroupId() { return entryGroupId; }
    public UUID getPaymentId() { return paymentId; }
    public String getEntryKey() { return entryKey; }
    public String getAccountCode() { return accountCode; }
    public String getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

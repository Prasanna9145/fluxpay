package com.prasanna.fluxpay.ledger;

import com.prasanna.fluxpay.model.Payment;
import com.prasanna.fluxpay.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {
    private final LedgerEntryRepository ledgerRepository;
    private final PaymentRepository paymentRepository;
    public LedgerService(LedgerEntryRepository ledgerRepository, PaymentRepository paymentRepository) {
        this.ledgerRepository = ledgerRepository; this.paymentRepository = paymentRepository;
    }
    @Transactional
    public void postCompletedPayment(Payment payment) {
        String debitKey = payment.getId() + ":processor-clearing";
        String creditKey = payment.getId() + ":merchant-payables";
        boolean debitExists = ledgerRepository.existsByEntryKey(debitKey);
        boolean creditExists = ledgerRepository.existsByEntryKey(creditKey);
        if (debitExists && creditExists) return;
        if (debitExists || creditExists) throw new IllegalStateException("Incomplete ledger group exists for payment " + payment.getId());
        BigDecimal debit = payment.getAmount();
        BigDecimal credit = payment.getAmount();
        LedgerBalanceRules.requireBalanced(debit, credit);
        UUID group = UUID.randomUUID();
        ledgerRepository.saveAll(List.of(
            new LedgerEntry(group, payment.getId(), debitKey, "PROCESSOR_CLEARING", "DEBIT", debit, payment.getCurrency()),
            new LedgerEntry(group, payment.getId(), creditKey, "MERCHANT_PAYABLES", "CREDIT", credit, payment.getCurrency())
        ));
    }
    @Transactional(readOnly = true)
    public List<LedgerEntry> forUser(String username) {
        List<UUID> ids = paymentRepository.findByUser_Username(username).stream().map(Payment::getId).toList();
        return ids.isEmpty() ? List.of() : ledgerRepository.findByPaymentIdInOrderByCreatedAtDesc(ids);
    }
    @Transactional(readOnly = true)
    public List<LedgerEntry> forPayment(UUID paymentId, String username) {
        paymentRepository.findByIdAndUser_Username(paymentId, username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        return ledgerRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }
}

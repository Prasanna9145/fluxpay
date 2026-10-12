package com.prasanna.fluxpay.ledger;

import java.math.BigDecimal;

/** Pure accounting rule used before writing a ledger group. Amounts are represented as positive values. */
public final class LedgerBalanceRules {
    private LedgerBalanceRules() {}
    public static boolean isBalanced(BigDecimal debitTotal, BigDecimal creditTotal) {
        return debitTotal != null && creditTotal != null && debitTotal.compareTo(creditTotal) == 0;
    }
    public static void requireBalanced(BigDecimal debitTotal, BigDecimal creditTotal) {
        if (!isBalanced(debitTotal, creditTotal)) {
            throw new IllegalStateException("Ledger group is not balanced: total debits must equal total credits");
        }
    }
}

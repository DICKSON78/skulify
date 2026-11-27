package com.skulify.pocketmoney.ledger;

import java.util.UUID;

/**
 * One side of a balanced money movement. Wallet postings must use
 * {@link AccountCode#STUDENT_WALLET} and include the wallet id; clearing,
 * revenue and float postings leave the wallet id null.
 */
public record LedgerPosting(
        UUID walletId,
        String accountCode,
        String counterAccountCode,
        Direction direction,
        long amount,
        LedgerEntryType type) {

    public static LedgerPosting walletPosting(
            UUID walletId, Direction direction, long amount, LedgerEntryType type, String counterAccount) {
        return new LedgerPosting(
                walletId, AccountCode.STUDENT_WALLET, counterAccount, direction, amount, type);
    }

    public static LedgerPosting clearingPosting(
            String accountCode, Direction direction, long amount, LedgerEntryType type, String counterAccount) {
        return new LedgerPosting(null, accountCode, counterAccount, direction, amount, type);
    }
}

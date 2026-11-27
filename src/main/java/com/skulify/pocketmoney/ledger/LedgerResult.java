package com.skulify.pocketmoney.ledger;

import java.util.Map;
import java.util.UUID;

public record LedgerResult(
        UUID referenceId,
        boolean replayed,
        Map<UUID, Long> balanceAfterByWallet,
        Map<String, Long> amountsByTypeAndAccount) {

    public static String amountKey(LedgerEntryType type, String accountCode) {
        return type.name() + "|" + accountCode;
    }
}

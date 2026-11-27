package com.skulify.pocketmoney.wallet;

import java.util.UUID;

public record PurchaseResult(
        UUID referenceId,
        long amount,
        long balanceAfter,
        boolean replayed) {
}

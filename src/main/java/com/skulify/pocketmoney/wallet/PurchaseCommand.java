package com.skulify.pocketmoney.wallet;

import java.util.UUID;

public record PurchaseCommand(
        UUID walletId,
        long amount,
        UUID merchantId,
        UUID terminalId,
        String idempotencyKey,
        UUID createdBy) {
}

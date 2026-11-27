package com.skulify.pocketmoney.wallet;

import java.util.UUID;

public record TopUpCommand(
        UUID walletId,
        long grossAmount,
        UUID parentUserId,
        String idempotencyKey,
        UUID createdBy) {
}

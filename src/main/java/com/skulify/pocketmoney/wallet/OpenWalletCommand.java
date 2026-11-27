package com.skulify.pocketmoney.wallet;

import java.util.UUID;

public record OpenWalletCommand(
        UUID id,
        UUID tenantId,
        UUID studentId,
        String idempotencyKey,
        long dailyLimit,
        long weeklyLimit,
        long monthlyLimit,
        long perTxnLimit,
        UUID createdBy) {
}

package com.skulify.pocketmoney.wallet;

import java.time.Instant;
import java.util.UUID;

/** Refund command. {@code alreadyRefunded} protects against cumulative over-refunds. */
public record RefundCommand(
        UUID walletId,
        long amount,
        long originalSaleTotal,
        long alreadyRefunded,
        Instant originalSaleAt,
        String idempotencyKey,
        UUID createdBy) {
}

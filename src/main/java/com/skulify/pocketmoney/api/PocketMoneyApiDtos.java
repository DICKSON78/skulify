package com.skulify.pocketmoney.api;

import java.time.Instant;
import java.util.UUID;

/** Request and response shapes for the ledger-backed Pocket Money API. */
public final class PocketMoneyApiDtos {

    private PocketMoneyApiDtos() {
    }

    public record OpenWalletRequest(
            UUID studentId,
            Long dailyLimit,
            Long weeklyLimit,
            Long monthlyLimit,
            Long perTransactionLimit) {
    }

    public record TopUpRequest(
            UUID studentId,
            UUID walletId,
            long amount,
            String provider,
            String providerReference) {
    }

    public record WalletResponse(
            UUID id,
            UUID studentId,
            long balance,
            String currency,
            String status,
            long dailyLimit,
            long weeklyLimit,
            long monthlyLimit,
            long perTransactionLimit) {
    }

    public record TopUpResponse(
            UUID id,
            UUID walletId,
            long grossAmount,
            long feeAmount,
            long netAmount,
            String currency,
            String provider,
            String providerReference,
            String status,
            Long balanceAfter,
            boolean replayed,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record LedgerEntryResponse(
            UUID id,
            UUID walletId,
            UUID referenceId,
            String type,
            String direction,
            long amount,
            String currency,
            Long balanceAfter,
            String accountCode,
            String counterAccountCode,
            String idempotencyKey,
            Instant createdAt) {
    }

    public record ReconciliationResponse(
            UUID walletId,
            long cachedBalance,
            long ledgerBalance,
            long difference,
            boolean healthy) {
    }
}

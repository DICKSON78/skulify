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

    public record IssueCardRequest(UUID studentId, String cardUid) {
    }

    public record CardResponse(
            UUID id,
            UUID studentId,
            UUID walletId,
            String uid,
            String status,
            Instant issuedAt) {
    }

    public record MerchantRequest(String code, String name, String category) {
    }

    public record MerchantResponse(UUID id, String code, String name, String category) {
    }

    public record TerminalRequest(String merchantCode, String code, String deviceKey) {
    }

    public record TerminalResponse(
            UUID id,
            String code,
            String merchantCode,
            String status,
            Instant lastSeenAt) {
    }

    public record PurchaseRequest(
            UUID studentId,
            String cardUid,
            String terminalCode,
            String deviceKey,
            long amount,
            String items,
            boolean offline,
            String nonce,
            Instant requestTimestamp) {
    }

    public record PurchaseResponse(
            UUID id,
            UUID walletId,
            UUID referenceId,
            long amount,
            String currency,
            String status,
            long balanceAfter,
            boolean replayed,
            String items,
            Instant createdAt) {
    }

    public record WalletLimitsRequest(
            Long dailyLimit,
            Long weeklyLimit,
            Long monthlyLimit,
            Long perTransactionLimit) {
    }
}

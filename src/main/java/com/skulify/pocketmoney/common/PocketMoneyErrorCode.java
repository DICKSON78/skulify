package com.skulify.pocketmoney.common;

/** Stable error codes returned by Pocket Money endpoints. */
public enum PocketMoneyErrorCode {
    INVALID_AMOUNT,
    IDEMPOTENCY_REQUIRED,
    IDEMPOTENCY_CONFLICT,
    DUPLICATE_REQUEST,
    WALLET_NOT_FOUND,
    WALLET_FROZEN,
    INSUFFICIENT_FUNDS,
    LIMIT_EXCEEDED,
    CARD_BLOCKED,
    UNBALANCED_POSTING,
    CURRENCY_MISMATCH,
    RECONCILIATION_MISMATCH
}

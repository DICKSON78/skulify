package com.skulify.pocketmoney.wallet;

import java.util.UUID;

public record RefundResult(
        UUID referenceId,
        long amount,
        long balanceAfter,
        boolean replayed) {
}

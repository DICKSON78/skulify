package com.skulify.pocketmoney.wallet;

import java.util.UUID;

public record TopUpResult(
        UUID referenceId,
        long grossAmount,
        long feeAmount,
        long netAmount,
        long balanceAfter,
        boolean replayed) {
}

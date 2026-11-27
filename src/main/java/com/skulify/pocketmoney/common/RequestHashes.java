package com.skulify.pocketmoney.common;

import com.skulify.pocketmoney.ledger.Direction;
import com.skulify.pocketmoney.ledger.LedgerPosting;
import com.skulify.pocketmoney.ledger.LedgerPostingRequest;
import com.skulify.pocketmoney.wallet.OpenWalletCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stable request fingerprints used to detect idempotency-key reuse with different inputs. */
public final class RequestHashes {

    private RequestHashes() {
    }

    public static String forLedger(LedgerPostingRequest request) {
        List<String> postings = new ArrayList<>();
        if (request.postings() == null) {
            request = new LedgerPostingRequest(
                    request.idempotencyKey(),
                    request.scope(),
                    request.referenceId(),
                    request.tenantId(),
                    request.createdBy(),
                    List.of(),
                    request.metadata());
        }
        for (LedgerPosting posting : request.postings()) {
            postings.add(String.join("|",
                    nullSafe(posting.walletId()),
                    posting.accountCode(),
                    posting.counterAccountCode(),
                    posting.direction() == Direction.DEBIT ? "DEBIT" : "CREDIT",
                    Long.toString(posting.amount()),
                    posting.type().name()));
        }
        postings.sort(Comparator.naturalOrder());
        Map<String, String> values = request.metadata() == null ? Map.of() : request.metadata();
        List<String> metadata = new ArrayList<>();
        values.forEach((name, value) -> metadata.add(name + "=" + nullSafe(value)));
        metadata.sort(Comparator.naturalOrder());

        // Commands with derived postings (for example a configurable top-up fee)
        // supply a stable business fingerprint. Raw ledger callers fall back to
        // the postings themselves.
        String businessFingerprint = values.get("businessFingerprint");
        String operationPart = businessFingerprint == null
                ? String.join(";", postings)
                : businessFingerprint;
        return sha256(String.join(";",
                request.scope(),
                nullSafe(request.referenceId()),
                nullSafe(request.tenantId()),
                nullSafe(request.createdBy()),
                operationPart,
                String.join(";", metadata)));
    }

    public static String forWalletOpen(OpenWalletCommand command) {
        return sha256(String.join("|",
                "WALLET_OPEN",
                nullSafe(command.id()),
                nullSafe(command.tenantId()),
                nullSafe(command.studentId()),
                nullSafe(command.createdBy()),
                Long.toString(command.dailyLimit()),
                Long.toString(command.weeklyLimit()),
                Long.toString(command.monthlyLimit()),
                Long.toString(command.perTxnLimit())));
    }

    private static String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                result.append(String.format("%02x", part));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

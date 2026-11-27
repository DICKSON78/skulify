package com.skulify.pocketmoney.ledger;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LedgerPostingRequest(
        String idempotencyKey,
        String scope,
        UUID referenceId,
        UUID tenantId,
        UUID createdBy,
        List<LedgerPosting> postings,
        Map<String, String> metadata) {

    public LedgerPostingRequest(
            String idempotencyKey,
            String scope,
            UUID referenceId,
            UUID tenantId,
            UUID createdBy,
            List<LedgerPosting> postings) {
        this(idempotencyKey, scope, referenceId, tenantId, createdBy, postings, Map.of());
    }
}

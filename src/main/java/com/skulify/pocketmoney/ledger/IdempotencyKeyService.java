package com.skulify.pocketmoney.ledger;

import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reads and validates the idempotency records that guard every money write. */
@Component
public class IdempotencyKeyService {

    private final IdempotencyKeyRepository repository;

    public IdempotencyKeyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    public Optional<PmIdempotencyKey> find(String key) {
        return repository.findById(key);
    }

    public void verify(PmIdempotencyKey key, String scope, String requestHash) {
        if (!scope.equals(key.getScope())) {
            throw conflict("idempotency key was used by a different operation");
        }
        if (!requestHash.equals(key.getRequestHash())) {
            throw conflict("idempotency key was reused with different request details");
        }
        if (key.getResultRef() == null) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.DUPLICATE_REQUEST, "Idempotency key has no completed result");
        }
        try {
            UUID.fromString(key.getResultRef());
        } catch (IllegalArgumentException exception) {
            throw new PocketMoneyException(
                    PocketMoneyErrorCode.DUPLICATE_REQUEST, "Idempotency key has an invalid result reference");
        }
    }

    public PmIdempotencyKey newKey(String key, String scope, String requestHash, String resultRef) {
        PmIdempotencyKey record = new PmIdempotencyKey();
        record.setKey(key);
        record.setScope(scope);
        record.setRequestHash(requestHash);
        record.setResultRef(resultRef);
        record.setCreatedAt(Instant.now());
        return record;
    }

    private PocketMoneyException conflict(String detail) {
        return new PocketMoneyException(PocketMoneyErrorCode.IDEMPOTENCY_CONFLICT, detail);
    }
}

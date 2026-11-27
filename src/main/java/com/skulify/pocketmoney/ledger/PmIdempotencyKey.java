package com.skulify.pocketmoney.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pm_idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
public class PmIdempotencyKey {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String key;

    @Column(name = "scope", nullable = false, length = 60)
    private String scope;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "result_ref", length = 80)
    private String resultRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PmIdempotencyKey(String key, String scope, String requestHash, String resultRef) {
        this.key = key;
        this.scope = scope;
        this.requestHash = requestHash;
        this.resultRef = resultRef;
        this.createdAt = Instant.now();
    }
}

package com.skulify.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Stores the result of a retail write so a retried key cannot repeat the command. */
@Entity
@Table(name = "retail_write_idempotency")
@Getter
@Setter
@NoArgsConstructor
public class RetailWriteIdempotency {

    @EmbeddedId
    private RetailWriteIdempotencyId id;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "result_ref")
    private UUID resultRef;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

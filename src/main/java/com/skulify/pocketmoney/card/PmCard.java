package com.skulify.pocketmoney.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A student's NFC/RFID/QR credential. Only the hash of the UID is stored. */
@Entity
@Table(name = "pm_cards")
@Getter
@Setter
@NoArgsConstructor
public class PmCard {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "uid_hash", nullable = false, updatable = false, length = 64)
    private String uidHash;

    /** Display-only copy of the card identifier; the secret is the terminal device key. */
    @Column(name = "uid_label", length = 60)
    private String uidLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private PmCardStatus status = PmCardStatus.ISSUED;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

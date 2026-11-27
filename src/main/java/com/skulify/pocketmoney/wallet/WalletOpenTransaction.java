package com.skulify.pocketmoney.wallet;

import com.skulify.pocketmoney.ledger.IdempotencyKeyService;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Atomically creates a wallet and claims the idempotency key that identifies it. */
@Component
public class WalletOpenTransaction {

    private final WalletRepository walletRepository;
    private final IdempotencyKeyService idempotencyKeys;
    private final EntityManager entityManager;

    public WalletOpenTransaction(
            WalletRepository walletRepository,
            IdempotencyKeyService idempotencyKeys,
            EntityManager entityManager) {
        this.walletRepository = walletRepository;
        this.idempotencyKeys = idempotencyKeys;
        this.entityManager = entityManager;
    }

    @Transactional
    public PmWallet open(OpenWalletCommand command, String requestHash) {
        var existing = idempotencyKeys.find(command.idempotencyKey());
        if (existing.isPresent()) {
            idempotencyKeys.verify(existing.get(), "WALLET_OPEN", requestHash);
            return walletById(UUID.fromString(existing.get().getResultRef()));
        }

        PmWallet wallet = walletRepository.findByStudentId(command.studentId()).orElseGet(() -> {
            PmWallet created = new PmWallet(
                    command.id(),
                    command.tenantId(),
                    command.studentId(),
                    command.dailyLimit(),
                    command.perTxnLimit());
            created.setWeeklyLimit(command.weeklyLimit());
            created.setMonthlyLimit(command.monthlyLimit());
            return walletRepository.saveAndFlush(created);
        });
        claim(command, requestHash, wallet.getId());
        return wallet;
    }

    @Transactional
    public PmWallet claimExisting(OpenWalletCommand command, String requestHash, UUID walletId) {
        var existing = idempotencyKeys.find(command.idempotencyKey());
        if (existing.isPresent()) {
            idempotencyKeys.verify(existing.get(), "WALLET_OPEN", requestHash);
            return walletById(UUID.fromString(existing.get().getResultRef()));
        }
        claim(command, requestHash, walletId);
        return walletById(walletId);
    }

    private void claim(OpenWalletCommand command, String requestHash, UUID walletId) {
        entityManager.persist(idempotencyKeys.newKey(
                command.idempotencyKey(), "WALLET_OPEN", requestHash, walletId.toString()));
        entityManager.flush();
    }

    private PmWallet walletById(UUID id) {
        return walletRepository.findById(id).orElseThrow(() ->
                new IllegalStateException("Wallet not found for idempotency result: " + id));
    }
}

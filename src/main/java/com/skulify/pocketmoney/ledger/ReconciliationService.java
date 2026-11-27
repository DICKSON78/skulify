package com.skulify.pocketmoney.ledger;

import com.skulify.pocketmoney.common.PocketMoneyErrorCode;
import com.skulify.pocketmoney.common.PocketMoneyException;
import com.skulify.pocketmoney.wallet.PmWallet;
import com.skulify.pocketmoney.wallet.WalletRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recomputes each wallet from the append-only ledger and alerts on drift. */
@Service
public class ReconciliationService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReconciliationService(
            WalletRepository walletRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ApplicationEventPublisher eventPublisher) {
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public ReconciliationResult reconcile(UUID walletId) {
        PmWallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new PocketMoneyException(
                        PocketMoneyErrorCode.WALLET_NOT_FOUND, "Wallet not found: " + walletId));
        return buildResult(wallet);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationResult> reconcileAll() {
        return walletRepository.findAll().stream().map(this::buildResult).toList();
    }

    private ReconciliationResult buildResult(PmWallet wallet) {
        long ledgerBalance = ledgerEntryRepository.sumSignedByWallet(wallet.getId());
        ReconciliationResult result = new ReconciliationResult(
                wallet.getId(), wallet.getBalance(), ledgerBalance, wallet.getBalance() == ledgerBalance);
        if (!result.healthy()) {
            eventPublisher.publishEvent(new ReconciliationMismatchEvent(
                    wallet.getId(), wallet.getBalance(), ledgerBalance));
        }
        return result;
    }
}

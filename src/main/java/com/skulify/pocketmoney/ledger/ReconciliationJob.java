package com.skulify.pocketmoney.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly check that every cached wallet balance still matches its ledger. */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ReconciliationService reconciliationService;

    public ReconciliationJob(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(
            cron = "${skulify.pocket-money.reconciliation-cron:0 30 2 * * *}",
            zone = "UTC")
    public void run() {
        var results = reconciliationService.reconcileAll();
        long mismatches = results.stream().filter(result -> !result.healthy()).count();
        log.info("Pocket Money reconciliation checked {} wallets, {} mismatches", results.size(), mismatches);
    }
}

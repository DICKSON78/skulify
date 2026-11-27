package com.skulify.pocketmoney.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Phase 1 alerting: log a high-severity event; Phase 5 can forward to the notification outbox. */
@Component
public class ReconciliationAlertListener {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationAlertListener.class);

    @EventListener
    public void onMismatch(ReconciliationMismatchEvent event) {
        log.error(
                "Pocket Money reconciliation mismatch: wallet={} cached={} ledger={}",
                event.walletId(),
                event.cachedBalance(),
                event.ledgerBalance());
    }
}

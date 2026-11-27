package com.skulify.pocketmoney;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration for the Pocket Money module. Amounts are whole TZS shillings. */
@Component
@ConfigurationProperties(prefix = "skulify.pocket-money")
public class PocketMoneyProperties {

    /** Fee charged once on top-up. 0.02 means 2%. */
    private BigDecimal topupFeePercent = new BigDecimal("0.02");

    /** Maximum value of a queued offline terminal approval. */
    private long offlineCap = 10_000L;

    /** Merchant reversal window. */
    private int refundWindowMinutes = 30;

    /** Default parent-approved cash limit per day. */
    private long dailyCashLimit = 50_000L;

    /** Nightly reconciliation schedule. */
    private String reconciliationCron = "0 30 2 * * *";

    public BigDecimal getTopupFeePercent() {
        return topupFeePercent;
    }

    public void setTopupFeePercent(BigDecimal topupFeePercent) {
        this.topupFeePercent = topupFeePercent;
    }

    public long getOfflineCap() {
        return offlineCap;
    }

    public void setOfflineCap(long offlineCap) {
        this.offlineCap = offlineCap;
    }

    public int getRefundWindowMinutes() {
        return refundWindowMinutes;
    }

    public void setRefundWindowMinutes(int refundWindowMinutes) {
        this.refundWindowMinutes = refundWindowMinutes;
    }

    public long getDailyCashLimit() {
        return dailyCashLimit;
    }

    public void setDailyCashLimit(long dailyCashLimit) {
        this.dailyCashLimit = dailyCashLimit;
    }

    public String getReconciliationCron() {
        return reconciliationCron;
    }

    public void setReconciliationCron(String reconciliationCron) {
        this.reconciliationCron = reconciliationCron;
    }
}

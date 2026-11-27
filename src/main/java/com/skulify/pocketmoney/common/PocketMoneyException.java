package com.skulify.pocketmoney.common;

/** Domain exception carrying a client-safe error code. */
public class PocketMoneyException extends RuntimeException {

    private final PocketMoneyErrorCode code;

    public PocketMoneyException(PocketMoneyErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public PocketMoneyErrorCode code() {
        return code;
    }
}

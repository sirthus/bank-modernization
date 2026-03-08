package com.modernize.bankbatch.exception;

public class ValidationException extends RuntimeException {

    private final Integer stagedTransactionId;

    public ValidationException(Integer stagedTransactionId, String message) {
        super(message);
        this.stagedTransactionId = stagedTransactionId;
    }

    public Integer getStagedTransactionId() {
        return stagedTransactionId;
    }
}

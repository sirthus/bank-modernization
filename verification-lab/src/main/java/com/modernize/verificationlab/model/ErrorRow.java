package com.modernize.verificationlab.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One row from bank.batch_job_errors (or the baseline equivalent).
 *
 * Captures the rejection reason and the staged record it refers to.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorRow {

    private String errorMessage;
    private String recordRef;

    public ErrorRow() {}

    public ErrorRow(String errorMessage, String recordRef) {
        this.errorMessage = errorMessage;
        this.recordRef = recordRef;
    }

    public String getErrorMessage() { return errorMessage; }
    public String getRecordRef()    { return recordRef; }

    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setRecordRef(String recordRef)       { this.recordRef = recordRef; }
}

package com.modernize.verificationlab.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The legacy-style expected output for one dataset, deserialized from baseline.json.
 *
 * This is a human-authored document representing what the legacy system would have
 * produced for the same input. It is not an automated snapshot — it captures intent.
 *
 * The reconciliation field represents what the legacy system's own reconciliation
 * check would have reported (staged-vs-posted balance from the legacy perspective).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaselineOutput {

    private String datasetId;
    private String description;
    private int stagedCount;
    private int postedCount;
    private int rejectedCount;
    private int errorCount;
    private long totalPostedCents;
    private ReconciliationExpectation reconciliation;
    private List<TransactionRow> transactions;
    private List<ErrorRow> errors;

    public String getDatasetId()                           { return datasetId; }
    public String getDescription()                         { return description; }
    public int getStagedCount()                            { return stagedCount; }
    public int getPostedCount()                            { return postedCount; }
    public int getRejectedCount()                          { return rejectedCount; }
    public int getErrorCount()                             { return errorCount; }
    public long getTotalPostedCents()                      { return totalPostedCents; }
    public ReconciliationExpectation getReconciliation()   { return reconciliation; }
    public List<TransactionRow> getTransactions()          { return transactions; }
    public List<ErrorRow> getErrors()                      { return errors; }

    public void setDatasetId(String datasetId)                                  { this.datasetId = datasetId; }
    public void setDescription(String description)                               { this.description = description; }
    public void setStagedCount(int stagedCount)                                  { this.stagedCount = stagedCount; }
    public void setPostedCount(int postedCount)                                  { this.postedCount = postedCount; }
    public void setRejectedCount(int rejectedCount)                              { this.rejectedCount = rejectedCount; }
    public void setErrorCount(int errorCount)                                    { this.errorCount = errorCount; }
    public void setTotalPostedCents(long totalPostedCents)                       { this.totalPostedCents = totalPostedCents; }
    public void setReconciliation(ReconciliationExpectation reconciliation)      { this.reconciliation = reconciliation; }
    public void setTransactions(List<TransactionRow> transactions)               { this.transactions = transactions; }
    public void setErrors(List<ErrorRow> errors)                                 { this.errors = errors; }
}

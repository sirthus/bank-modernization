package com.modernize.verificationlab.model;

import java.util.List;

/**
 * The actual outputs produced by the modernized pipeline for one dataset run.
 *
 * Populated by ActualOutputCollector via JdbcTemplate queries against the
 * bank schema after the pipeline has completed. All counts and totals are
 * scoped to the current run (database is cleaned before each test).
 */
public class ActualOutput {

    private int stagedCount;
    private int postedCount;
    private int rejectedCount;
    private int errorCount;
    private long totalPostedCents;
    private boolean reconciliationCountsMatch;
    private boolean reconciliationTotalsMatch;
    private List<TransactionRow> transactions;
    private List<ErrorRow> errors;

    public int getStagedCount()                    { return stagedCount; }
    public int getPostedCount()                    { return postedCount; }
    public int getRejectedCount()                  { return rejectedCount; }
    public int getErrorCount()                     { return errorCount; }
    public long getTotalPostedCents()              { return totalPostedCents; }
    public boolean isReconciliationCountsMatch()   { return reconciliationCountsMatch; }
    public boolean isReconciliationTotalsMatch()   { return reconciliationTotalsMatch; }
    public List<TransactionRow> getTransactions()  { return transactions; }
    public List<ErrorRow> getErrors()              { return errors; }

    public void setStagedCount(int stagedCount)                              { this.stagedCount = stagedCount; }
    public void setPostedCount(int postedCount)                              { this.postedCount = postedCount; }
    public void setRejectedCount(int rejectedCount)                          { this.rejectedCount = rejectedCount; }
    public void setErrorCount(int errorCount)                                { this.errorCount = errorCount; }
    public void setTotalPostedCents(long totalPostedCents)                   { this.totalPostedCents = totalPostedCents; }
    public void setReconciliationCountsMatch(boolean reconciliationCountsMatch) { this.reconciliationCountsMatch = reconciliationCountsMatch; }
    public void setReconciliationTotalsMatch(boolean reconciliationTotalsMatch) { this.reconciliationTotalsMatch = reconciliationTotalsMatch; }
    public void setTransactions(List<TransactionRow> transactions)           { this.transactions = transactions; }
    public void setErrors(List<ErrorRow> errors)                             { this.errors = errors; }
}

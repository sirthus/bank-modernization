package com.modernize.verificationlab.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The reconciliation flags expected in the baseline.
 *
 * Represents what bank.batch_reconciliations should show for this run —
 * whether the pipeline's own staged-vs-posted check came out clean.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReconciliationExpectation {

    private boolean countsMatch;
    private boolean totalsMatch;

    public ReconciliationExpectation() {}

    public boolean isCountsMatch() { return countsMatch; }
    public boolean isTotalsMatch() { return totalsMatch; }

    public void setCountsMatch(boolean countsMatch) { this.countsMatch = countsMatch; }
    public void setTotalsMatch(boolean totalsMatch) { this.totalsMatch = totalsMatch; }
}

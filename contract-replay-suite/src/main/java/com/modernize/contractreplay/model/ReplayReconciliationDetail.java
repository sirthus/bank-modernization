package com.modernize.contractreplay.model;

public class ReplayReconciliationDetail {

    private int runNumber;
    private int batchId;
    private int stagedCount;
    private int postedCount;
    private long stagedTotalCents;
    private long postedTotalCents;
    private boolean countsMatch;
    private boolean totalsMatch;
    private Boolean expectationMatch;

    public int getRunNumber() {
        return runNumber;
    }

    public void setRunNumber(int runNumber) {
        this.runNumber = runNumber;
    }

    public int getBatchId() {
        return batchId;
    }

    public void setBatchId(int batchId) {
        this.batchId = batchId;
    }

    public int getStagedCount() {
        return stagedCount;
    }

    public void setStagedCount(int stagedCount) {
        this.stagedCount = stagedCount;
    }

    public int getPostedCount() {
        return postedCount;
    }

    public void setPostedCount(int postedCount) {
        this.postedCount = postedCount;
    }

    public long getStagedTotalCents() {
        return stagedTotalCents;
    }

    public void setStagedTotalCents(long stagedTotalCents) {
        this.stagedTotalCents = stagedTotalCents;
    }

    public long getPostedTotalCents() {
        return postedTotalCents;
    }

    public void setPostedTotalCents(long postedTotalCents) {
        this.postedTotalCents = postedTotalCents;
    }

    public boolean isCountsMatch() {
        return countsMatch;
    }

    public void setCountsMatch(boolean countsMatch) {
        this.countsMatch = countsMatch;
    }

    public boolean isTotalsMatch() {
        return totalsMatch;
    }

    public void setTotalsMatch(boolean totalsMatch) {
        this.totalsMatch = totalsMatch;
    }

    public Boolean getExpectationMatch() {
        return expectationMatch;
    }

    public void setExpectationMatch(Boolean expectationMatch) {
        this.expectationMatch = expectationMatch;
    }
}

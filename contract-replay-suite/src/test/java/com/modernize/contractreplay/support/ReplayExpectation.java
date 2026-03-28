package com.modernize.contractreplay.support;

import com.fasterxml.jackson.databind.JsonNode;

public class ReplayExpectation {

    private String scenarioId;
    private String description;
    private String purpose;
    private String fixtureFile;
    private JsonNode fixtureContents;
    private Integer runsSubmitted;
    private Integer expectedStagedCount;
    private Integer expectedPostedCount;
    private Integer expectedRejectedCount;
    private Integer expectedErrorCount;
    private Long expectedTotalPostedCents;
    private PerRunExpectation expectedPerRun;
    private ReconciliationExpectation expectedReconciliation;
    private AggregateExpectation expectedAfterBothRuns;
    private String whatWouldBreakThis;

    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getFixtureFile() {
        return fixtureFile;
    }

    public void setFixtureFile(String fixtureFile) {
        this.fixtureFile = fixtureFile;
    }

    public JsonNode getFixtureContents() {
        return fixtureContents;
    }

    public void setFixtureContents(JsonNode fixtureContents) {
        this.fixtureContents = fixtureContents;
    }

    public Integer getRunsSubmitted() {
        return runsSubmitted;
    }

    public void setRunsSubmitted(Integer runsSubmitted) {
        this.runsSubmitted = runsSubmitted;
    }

    public Integer getExpectedStagedCount() {
        return expectedStagedCount;
    }

    public void setExpectedStagedCount(Integer expectedStagedCount) {
        this.expectedStagedCount = expectedStagedCount;
    }

    public Integer getExpectedPostedCount() {
        return expectedPostedCount;
    }

    public void setExpectedPostedCount(Integer expectedPostedCount) {
        this.expectedPostedCount = expectedPostedCount;
    }

    public Integer getExpectedRejectedCount() {
        return expectedRejectedCount;
    }

    public void setExpectedRejectedCount(Integer expectedRejectedCount) {
        this.expectedRejectedCount = expectedRejectedCount;
    }

    public Integer getExpectedErrorCount() {
        return expectedErrorCount;
    }

    public void setExpectedErrorCount(Integer expectedErrorCount) {
        this.expectedErrorCount = expectedErrorCount;
    }

    public Long getExpectedTotalPostedCents() {
        return expectedTotalPostedCents;
    }

    public void setExpectedTotalPostedCents(Long expectedTotalPostedCents) {
        this.expectedTotalPostedCents = expectedTotalPostedCents;
    }

    public PerRunExpectation getExpectedPerRun() {
        return expectedPerRun;
    }

    public void setExpectedPerRun(PerRunExpectation expectedPerRun) {
        this.expectedPerRun = expectedPerRun;
    }

    public ReconciliationExpectation getExpectedReconciliation() {
        return expectedReconciliation;
    }

    public void setExpectedReconciliation(ReconciliationExpectation expectedReconciliation) {
        this.expectedReconciliation = expectedReconciliation;
    }

    public AggregateExpectation getExpectedAfterBothRuns() {
        return expectedAfterBothRuns;
    }

    public void setExpectedAfterBothRuns(AggregateExpectation expectedAfterBothRuns) {
        this.expectedAfterBothRuns = expectedAfterBothRuns;
    }

    public String getWhatWouldBreakThis() {
        return whatWouldBreakThis;
    }

    public void setWhatWouldBreakThis(String whatWouldBreakThis) {
        this.whatWouldBreakThis = whatWouldBreakThis;
    }

    public static class PerRunExpectation {
        private Integer stagedCount;
        private Integer postedCount;
        private Long stagedTotalCents;
        private Long postedTotalCents;
        private Boolean countsMatch;
        private Boolean totalsMatch;

        public Integer getStagedCount() {
            return stagedCount;
        }

        public void setStagedCount(Integer stagedCount) {
            this.stagedCount = stagedCount;
        }

        public Integer getPostedCount() {
            return postedCount;
        }

        public void setPostedCount(Integer postedCount) {
            this.postedCount = postedCount;
        }

        public Long getStagedTotalCents() {
            return stagedTotalCents;
        }

        public void setStagedTotalCents(Long stagedTotalCents) {
            this.stagedTotalCents = stagedTotalCents;
        }

        public Long getPostedTotalCents() {
            return postedTotalCents;
        }

        public void setPostedTotalCents(Long postedTotalCents) {
            this.postedTotalCents = postedTotalCents;
        }

        public Boolean getCountsMatch() {
            return countsMatch;
        }

        public void setCountsMatch(Boolean countsMatch) {
            this.countsMatch = countsMatch;
        }

        public Boolean getTotalsMatch() {
            return totalsMatch;
        }

        public void setTotalsMatch(Boolean totalsMatch) {
            this.totalsMatch = totalsMatch;
        }
    }

    public static class ReconciliationExpectation {
        private Integer rowCount;
        private Boolean countsMatch;
        private Boolean totalsMatch;
        private Integer stagedCount;
        private Integer postedCount;
        private Long stagedTotalCents;
        private Long postedTotalCents;
        private String note;

        public Integer getRowCount() {
            return rowCount;
        }

        public void setRowCount(Integer rowCount) {
            this.rowCount = rowCount;
        }

        public Boolean getCountsMatch() {
            return countsMatch;
        }

        public void setCountsMatch(Boolean countsMatch) {
            this.countsMatch = countsMatch;
        }

        public Boolean getTotalsMatch() {
            return totalsMatch;
        }

        public void setTotalsMatch(Boolean totalsMatch) {
            this.totalsMatch = totalsMatch;
        }

        public Integer getStagedCount() {
            return stagedCount;
        }

        public void setStagedCount(Integer stagedCount) {
            this.stagedCount = stagedCount;
        }

        public Integer getPostedCount() {
            return postedCount;
        }

        public void setPostedCount(Integer postedCount) {
            this.postedCount = postedCount;
        }

        public Long getStagedTotalCents() {
            return stagedTotalCents;
        }

        public void setStagedTotalCents(Long stagedTotalCents) {
            this.stagedTotalCents = stagedTotalCents;
        }

        public Long getPostedTotalCents() {
            return postedTotalCents;
        }

        public void setPostedTotalCents(Long postedTotalCents) {
            this.postedTotalCents = postedTotalCents;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    public static class AggregateExpectation {
        private Integer totalStagedTransactions;
        private Integer totalPostedTransactions;
        private Integer reconciliationRowCount;
        private Boolean eachRowCountsMatch;
        private Boolean eachRowTotalsMatch;

        public Integer getTotalStagedTransactions() {
            return totalStagedTransactions;
        }

        public void setTotalStagedTransactions(Integer totalStagedTransactions) {
            this.totalStagedTransactions = totalStagedTransactions;
        }

        public Integer getTotalPostedTransactions() {
            return totalPostedTransactions;
        }

        public void setTotalPostedTransactions(Integer totalPostedTransactions) {
            this.totalPostedTransactions = totalPostedTransactions;
        }

        public Integer getReconciliationRowCount() {
            return reconciliationRowCount;
        }

        public void setReconciliationRowCount(Integer reconciliationRowCount) {
            this.reconciliationRowCount = reconciliationRowCount;
        }

        public Boolean getEachRowCountsMatch() {
            return eachRowCountsMatch;
        }

        public void setEachRowCountsMatch(Boolean eachRowCountsMatch) {
            this.eachRowCountsMatch = eachRowCountsMatch;
        }

        public Boolean getEachRowTotalsMatch() {
            return eachRowTotalsMatch;
        }

        public void setEachRowTotalsMatch(Boolean eachRowTotalsMatch) {
            this.eachRowTotalsMatch = eachRowTotalsMatch;
        }
    }
}

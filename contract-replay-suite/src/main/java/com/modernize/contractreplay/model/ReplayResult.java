package com.modernize.contractreplay.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The result of one replay scenario run.
 *
 * Built from database queries after the pipeline has executed. Each Rp00*Test
 * constructs one of these and compares it against the scenario's expected-outcome.json.
 *
 * status reflects whether actual counts, reconciliation facts, and any per-run
 * expectations matched what the expected-outcome declared. The richer fields in
 * this type are used both for executable assertions and for human-readable
 * portfolio evidence.
 */
public class ReplayResult {

    private String scenarioId;
    private String description;
    private String purpose;
    private String whatWouldBreakThis;
    private ContractStatus status;
    private String expectedSummary;
    private String actualSummary;

    private int actualStagedCount;
    private int actualPostedCount;
    private int actualRejectedCount;
    private int actualErrorCount;
    private long actualTotalPostedCents;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private int reconciliationRowCount;
    private boolean reconciliationCountsMatch;
    private boolean reconciliationTotalsMatch;
    private Boolean perRunExpectationsMatch;
    private List<ReplayReconciliationDetail> reconciliationDetails;
    private List<String> mismatches;

    private String evaluatedAt;

    public ReplayResult() {
        this.status = ContractStatus.PASS;
        this.reconciliationDetails = new ArrayList<>();
        this.mismatches = new ArrayList<>();
        this.evaluatedAt = LocalDateTime.now().format(ISO);
    }

    public ReplayResult(String scenarioId, String description) {
        this();
        this.scenarioId = scenarioId;
        this.description = description;
    }

    public String getScenarioId()                  { return scenarioId; }
    public String getDescription()                 { return description; }
    public String getPurpose()                     { return purpose; }
    public String getWhatWouldBreakThis()          { return whatWouldBreakThis; }
    public ContractStatus getStatus()              { return status; }
    public String getExpectedSummary()             { return expectedSummary; }
    public String getActualSummary()               { return actualSummary; }
    public int getActualStagedCount()              { return actualStagedCount; }
    public int getActualPostedCount()              { return actualPostedCount; }
    public int getActualRejectedCount()            { return actualRejectedCount; }
    public int getActualErrorCount()               { return actualErrorCount; }
    public long getActualTotalPostedCents()        { return actualTotalPostedCents; }
    public int getReconciliationRowCount()         { return reconciliationRowCount; }
    public boolean isReconciliationCountsMatch()   { return reconciliationCountsMatch; }
    public boolean isReconciliationTotalsMatch()   { return reconciliationTotalsMatch; }
    public Boolean getPerRunExpectationsMatch()    { return perRunExpectationsMatch; }
    public List<ReplayReconciliationDetail> getReconciliationDetails() { return reconciliationDetails; }
    public List<String> getMismatches()            { return mismatches; }
    public String getEvaluatedAt()                 { return evaluatedAt; }

    public void setScenarioId(String scenarioId)               { this.scenarioId = scenarioId; }
    public void setDescription(String description)             { this.description = description; }
    public void setPurpose(String purpose)                     { this.purpose = purpose; }
    public void setWhatWouldBreakThis(String whatWouldBreakThis) { this.whatWouldBreakThis = whatWouldBreakThis; }
    public void setStatus(ContractStatus status)               { this.status = status; }
    public void setExpectedSummary(String expectedSummary)     { this.expectedSummary = expectedSummary; }
    public void setActualSummary(String actualSummary)         { this.actualSummary = actualSummary; }
    public void setActualStagedCount(int v)                    { this.actualStagedCount = v; }
    public void setActualPostedCount(int v)                    { this.actualPostedCount = v; }
    public void setActualRejectedCount(int v)                  { this.actualRejectedCount = v; }
    public void setActualErrorCount(int v)                     { this.actualErrorCount = v; }
    public void setActualTotalPostedCents(long v)              { this.actualTotalPostedCents = v; }
    public void setReconciliationRowCount(int v)               { this.reconciliationRowCount = v; }
    public void setReconciliationCountsMatch(boolean v)        { this.reconciliationCountsMatch = v; }
    public void setReconciliationTotalsMatch(boolean v)        { this.reconciliationTotalsMatch = v; }
    public void setPerRunExpectationsMatch(Boolean v)          { this.perRunExpectationsMatch = v; }
    public void setReconciliationDetails(List<ReplayReconciliationDetail> details) { this.reconciliationDetails = details; }
    public void setMismatches(List<String> mismatches)         { this.mismatches = mismatches; }
    public void setEvaluatedAt(String evaluatedAt)             { this.evaluatedAt = evaluatedAt; }
}

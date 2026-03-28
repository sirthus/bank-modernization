package com.modernize.contractreplay.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level evidence envelope for one full suite run.
 *
 * Contains all ContractResults (one per boundary) and all ReplayResults
 * (one per scenario). overallStatus is the worst-case across everything.
 *
 * This is what gets serialized to the JSON and Markdown evidence reports.
 *
 * timestamp is stored as an ISO-8601 String so Jackson serializes it as a
 * plain string without requiring the jackson-datatype-jsr310 module.
 */
public class SuiteResult {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private String suiteId;
    private String timestamp;
    private String environment;
    private ContractStatus overallStatus;

    private List<ContractResult> contractResults;
    private List<ReplayResult> replayResults;

    public SuiteResult() {
        this.suiteId = "contract-replay-suite";
        this.timestamp = LocalDateTime.now().format(ISO);
        this.environment = "contracttest";
        this.overallStatus = ContractStatus.PASS;
        this.contractResults = new ArrayList<>();
        this.replayResults = new ArrayList<>();
    }

    /**
     * Adds a contract boundary result and updates overallStatus.
     */
    public void addContractResult(ContractResult result) {
        this.contractResults.add(result);
        this.overallStatus = ContractStatus.worstOf(this.overallStatus, result.getOverallStatus());
    }

    /**
     * Adds a replay scenario result and updates overallStatus.
     */
    public void addReplayResult(ReplayResult result) {
        this.replayResults.add(result);
        this.overallStatus = ContractStatus.worstOf(this.overallStatus, result.getStatus());
    }

    public String getSuiteId()                          { return suiteId; }
    public String getTimestamp()                        { return timestamp; }
    public String getEnvironment()                      { return environment; }
    public ContractStatus getOverallStatus()            { return overallStatus; }
    public List<ContractResult> getContractResults()    { return contractResults; }
    public List<ReplayResult> getReplayResults()        { return replayResults; }

    public void setSuiteId(String suiteId)                          { this.suiteId = suiteId; }
    public void setTimestamp(String timestamp)                       { this.timestamp = timestamp; }
    public void setEnvironment(String environment)                   { this.environment = environment; }
    public void setOverallStatus(ContractStatus overallStatus)       { this.overallStatus = overallStatus; }
    public void setContractResults(List<ContractResult> results)     { this.contractResults = results; }
    public void setReplayResults(List<ReplayResult> results)         { this.replayResults = results; }
}

package com.modernize.verificationlab.model;

import java.util.List;

/**
 * The complete output of one verification run against one dataset.
 *
 * Contains the overall classification, per-finding breakdown, and metadata
 * needed to render both the JSON and HTML evidence reports.
 */
public class VerificationResult {

    private String datasetId;
    private String datasetDescription;
    private String timestamp;
    private String commitSha;
    private String environment;
    private DiscrepancyClassification overallStatus;
    private int passCount;
    private int warningCount;
    private int failCount;
    private int approvedCount;
    private List<DiscrepancyItem> discrepancies;  // non-PASS findings only

    public String getDatasetId()                           { return datasetId; }
    public String getDatasetDescription()                  { return datasetDescription; }
    public String getTimestamp()                           { return timestamp; }
    public String getCommitSha()                           { return commitSha; }
    public String getEnvironment()                         { return environment; }
    public DiscrepancyClassification getOverallStatus()    { return overallStatus; }
    public int getPassCount()                              { return passCount; }
    public int getWarningCount()                           { return warningCount; }
    public int getFailCount()                              { return failCount; }
    public int getApprovedCount()                          { return approvedCount; }
    public List<DiscrepancyItem> getDiscrepancies()        { return discrepancies; }

    public void setDatasetId(String datasetId)                         { this.datasetId = datasetId; }
    public void setDatasetDescription(String datasetDescription)       { this.datasetDescription = datasetDescription; }
    public void setTimestamp(String timestamp)                         { this.timestamp = timestamp; }
    public void setCommitSha(String commitSha)                         { this.commitSha = commitSha; }
    public void setEnvironment(String environment)                     { this.environment = environment; }
    public void setOverallStatus(DiscrepancyClassification overallStatus) { this.overallStatus = overallStatus; }
    public void setPassCount(int passCount)                            { this.passCount = passCount; }
    public void setWarningCount(int warningCount)                      { this.warningCount = warningCount; }
    public void setFailCount(int failCount)                            { this.failCount = failCount; }
    public void setApprovedCount(int approvedCount)                    { this.approvedCount = approvedCount; }
    public void setDiscrepancies(List<DiscrepancyItem> discrepancies)  { this.discrepancies = discrepancies; }
}

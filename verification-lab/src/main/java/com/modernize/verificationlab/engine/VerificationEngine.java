package com.modernize.verificationlab.engine;

import com.modernize.verificationlab.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates a full verification run for one dataset.
 *
 * Calls FieldComparator, rolls up counts, computes the overall worst-case
 * classification, and assembles the VerificationResult.
 */
public class VerificationEngine {

    /**
     * Runs a full field-by-field comparison for one dataset and assembles the result.
     *
     * @param actual      live output collected from the database after the pipeline run
     * @param baseline    checked-in expected output representing legacy system behaviour
     * @param approved    approved-difference entries for this dataset; empty list if none exist
     * @param commitSha   short SHA of the current commit, embedded in the report for traceability
     * @param environment label identifying the execution context (e.g. "verificationlab", "ci")
     * @return a VerificationResult containing per-field discrepancies, counts, and the overall
     *         worst-case classification across all compared fields
     */
    public VerificationResult compare(ActualOutput actual,
                                      BaselineOutput baseline,
                                      List<ApprovedDifference> approved,
                                      String commitSha,
                                      String environment) {

        FieldComparator comparator = new FieldComparator(approved);
        FieldComparator.Result comparison = comparator.compare(actual, baseline);

        List<DiscrepancyItem> allDiscrepancies = new ArrayList<>(comparison.discrepancies());

        // Roll up counts and overall status
        int passCount     = comparison.passCount();
        int warningCount  = 0;
        int failCount     = 0;
        int approvedCount = 0;

        DiscrepancyClassification overallStatus = DiscrepancyClassification.PASS;

        for (DiscrepancyItem item : allDiscrepancies) {
            overallStatus = DiscrepancyClassification.worstOf(overallStatus, item.getClassification());
            switch (item.getClassification()) {
                case WARNING             -> warningCount++;
                case FAIL                -> failCount++;
                case APPROVED_DIFFERENCE -> approvedCount++;
                default -> {} // PASS items are not stored in the discrepancy list
            }
        }

        VerificationResult result = new VerificationResult();
        result.setDatasetId(baseline.getDatasetId());
        result.setDatasetDescription(baseline.getDescription());
        result.setTimestamp(Instant.now().toString());
        result.setCommitSha(commitSha != null ? commitSha : "unknown");
        result.setEnvironment(environment != null ? environment : "verificationlab");
        result.setOverallStatus(overallStatus);
        result.setPassCount(passCount);
        result.setWarningCount(warningCount);
        result.setFailCount(failCount);
        result.setApprovedCount(approvedCount);
        result.setDiscrepancies(allDiscrepancies);

        return result;
    }
}

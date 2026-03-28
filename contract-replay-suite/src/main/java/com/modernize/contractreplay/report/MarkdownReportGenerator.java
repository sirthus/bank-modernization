package com.modernize.contractreplay.report;

import com.modernize.contractreplay.model.ContractCheck;
import com.modernize.contractreplay.model.ContractResult;
import com.modernize.contractreplay.model.ContractViolation;
import com.modernize.contractreplay.model.ReplayReconciliationDetail;
import com.modernize.contractreplay.model.ReplayResult;
import com.modernize.contractreplay.model.SuiteResult;

/**
 * Renders a SuiteResult as a Markdown report.
 *
 * Output structure:
 *   - Suite summary
 *   - Contract boundary matrix with executable checks
 *   - Replay scenario matrix with purpose and regression guard text
 *   - Violations / mismatches detail
 *
 * This is the human-readable artifact. It can be read by anyone reviewing the
 * CI build without needing to parse JSON or understand the codebase.
 */
public class MarkdownReportGenerator {

    public String generate(SuiteResult suite) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Contract + Replay Suite Report\n\n");

        sb.append("## Suite Summary\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| Suite ID | ").append(suite.getSuiteId()).append(" |\n");
        sb.append("| Timestamp | ").append(suite.getTimestamp()).append(" |\n");
        sb.append("| Environment | ").append(suite.getEnvironment()).append(" |\n");
        sb.append("| Overall Status | **").append(suite.getOverallStatus()).append("** |\n");
        sb.append("| Contract Boundaries | ").append(suite.getContractResults().size()).append(" |\n");
        sb.append("| Replay Scenarios | ").append(suite.getReplayResults().size()).append(" |\n");
        sb.append("\n");

        sb.append("## Contract Boundary Matrix\n\n");
        sb.append("| Contract ID | Boundary | Boundary Status | Check ID | Check Description | Check Status | Expected | Actual |\n");
        sb.append("|-------------|----------|-----------------|----------|-------------------|--------------|----------|--------|\n");
        for (ContractResult cr : suite.getContractResults()) {
            if (cr.getChecks().isEmpty()) {
                sb.append("| ").append(cr.getContractId())
                  .append(" | ").append(cr.getBoundaryName())
                  .append(" | ").append(statusBadge(cr.getOverallStatus().name()))
                  .append(" | n/a | n/a | n/a | n/a | n/a |\n");
                continue;
            }
            for (ContractCheck check : cr.getChecks()) {
                sb.append("| ").append(cr.getContractId())
                  .append(" | ").append(cr.getBoundaryName())
                  .append(" | ").append(statusBadge(cr.getOverallStatus().name()))
                  .append(" | ").append(check.getCheckId())
                  .append(" | ").append(escape(check.getDescription()))
                  .append(" | ").append(statusBadge(check.getStatus().name()))
                  .append(" | ").append(escape(check.getExpectedOutcome()))
                  .append(" | ").append(escape(check.getActualOutcome()))
                  .append(" |\n");
            }
        }
        sb.append("\n");

        sb.append("## Replay Scenario Matrix\n\n");
        sb.append("| Scenario | Status | Purpose | Regression Guard | Expected | Actual |\n");
        sb.append("|----------|--------|---------|------------------|----------|--------|\n");
        for (ReplayResult rr : suite.getReplayResults()) {
            sb.append("| ").append(rr.getScenarioId())
              .append(" | ").append(statusBadge(rr.getStatus().name()))
              .append(" | ").append(escape(rr.getPurpose()))
              .append(" | ").append(escape(rr.getWhatWouldBreakThis()))
              .append(" | ").append(escape(rr.getExpectedSummary()))
              .append(" | ").append(escape(rr.getActualSummary()))
              .append(" |\n");
        }
        sb.append("\n");

        for (ReplayResult rr : suite.getReplayResults()) {
            if (rr.getReconciliationDetails().isEmpty()) {
                continue;
            }
            sb.append("### ").append(rr.getScenarioId()).append(" Reconciliation Details\n\n");
            sb.append("| Run | Batch ID | Staged | Posted | Staged Total | Posted Total | Counts Match | Totals Match | Expectation Match |\n");
            sb.append("|-----|----------|--------|--------|--------------|--------------|--------------|--------------|-------------------|\n");
            for (ReplayReconciliationDetail detail : rr.getReconciliationDetails()) {
                sb.append("| ").append(detail.getRunNumber())
                  .append(" | ").append(detail.getBatchId())
                  .append(" | ").append(detail.getStagedCount())
                  .append(" | ").append(detail.getPostedCount())
                  .append(" | ").append(detail.getStagedTotalCents())
                  .append(" | ").append(detail.getPostedTotalCents())
                  .append(" | ").append(booleanBadge(detail.isCountsMatch()))
                  .append(" | ").append(booleanBadge(detail.isTotalsMatch()))
                  .append(" | ").append(optionalBooleanBadge(detail.getExpectationMatch()))
                  .append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("## Violations / Mismatches\n\n");

        boolean anyContractViolations = suite.getContractResults().stream()
            .anyMatch(cr -> !cr.getViolations().isEmpty());
        boolean anyReplayMismatches = suite.getReplayResults().stream()
            .anyMatch(rr -> !rr.getMismatches().isEmpty());

        if (!anyContractViolations && !anyReplayMismatches) {
            sb.append("No unexpected contract violations or replay mismatches were observed.\n");
            return sb.toString();
        }

        for (ContractResult cr : suite.getContractResults()) {
            if (cr.getViolations().isEmpty()) {
                continue;
            }
            sb.append("### ").append(cr.getBoundaryName()).append(" (").append(cr.getContractId()).append(")\n\n");
            sb.append("| Rule ID | Severity | Description | Expected | Actual |\n");
            sb.append("|---------|----------|-------------|----------|--------|\n");
            for (ContractViolation v : cr.getViolations()) {
                sb.append("| ").append(v.getRuleId())
                  .append(" | ").append(v.getSeverity())
                  .append(" | ").append(escape(v.getDescription()))
                  .append(" | ").append(escape(v.getExpected()))
                  .append(" | ").append(escape(v.getActual()))
                  .append(" |\n");
            }
            sb.append("\n");
        }

        for (ReplayResult rr : suite.getReplayResults()) {
            if (rr.getMismatches().isEmpty()) {
                continue;
            }
            sb.append("### ").append(rr.getScenarioId()).append(" Replay Mismatches\n\n");
            for (String mismatch : rr.getMismatches()) {
                sb.append("- ").append(mismatch).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String statusBadge(String status) {
        return switch (status) {
            case "PASS"    -> "PASS";
            case "WARNING" -> "WARNING";
            case "FAIL"    -> "**FAIL**";
            default        -> status;
        };
    }

    private String booleanBadge(boolean value) {
        return value ? "true" : "**false**";
    }

    private String optionalBooleanBadge(Boolean value) {
        if (value == null) {
            return "n/a";
        }
        return booleanBadge(value);
    }

    /** Escapes pipe characters so they don't break Markdown table cells. */
    private String escape(String value) {
        if (value == null) return "";
        return value.replace("|", "\\|");
    }
}

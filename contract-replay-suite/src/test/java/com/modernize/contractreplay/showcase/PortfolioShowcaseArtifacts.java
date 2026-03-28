package com.modernize.contractreplay.showcase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernize.contractreplay.model.ContractCheck;
import com.modernize.contractreplay.model.ContractResult;
import com.modernize.contractreplay.model.ContractStatus;
import com.modernize.contractreplay.model.ReplayReconciliationDetail;
import com.modernize.contractreplay.model.ReplayResult;
import com.modernize.contractreplay.model.SuiteResult;
import com.modernize.contractreplay.report.JsonReportGenerator;
import com.modernize.contractreplay.report.MarkdownReportGenerator;
import com.modernize.contractreplay.support.ReplayExpectation;
import com.modernize.contractreplay.support.ReplayExpectationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class PortfolioShowcaseArtifacts {

    public static final String SAMPLE_MARKDOWN_PATH = "docs/examples/portfolio-sample-suite-result.md";
    public static final String SAMPLE_JSON_PATH = "docs/examples/portfolio-sample-suite-result.json";
    public static final String GUARDRAIL_MATRIX_PATH = "docs/examples/guardrail-matrix.md";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PortfolioShowcaseArtifacts() {
    }

    public static String sampleMarkdown() {
        return new MarkdownReportGenerator().generate(sampleSuiteResult());
    }

    public static String sampleJson() {
        return new JsonReportGenerator().generate(sampleSuiteResult());
    }

    public static String guardrailMatrixDocument() {
        return "# Guardrail Matrix\n\n"
            + "This table is generated from executable metadata in the API contract JSON, "
            + "output invariant contract JSON, and replay expectation fixtures.\n\n"
            + guardrailMatrixTable();
    }

    public static String guardrailMatrixTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("| Type | ID | Description | Guards Against |\n");
        sb.append("|------|----|-------------|----------------|\n");
        appendApiScenarioRows(sb, "contracts/api/batch-run-contract.json");
        appendApiScenarioRows(sb, "contracts/api/batch-status-contract.json");
        appendInvariantRows(sb, "contracts/output/reconciliation-invariants.json");
        appendReplayRows(sb, List.of(
            "fixtures/rp-001-expected-outcome.json",
            "fixtures/rp-002-expected-outcome.json",
            "fixtures/rp-003-expected-outcome.json"));
        return sb.toString();
    }

    public static SuiteResult sampleSuiteResult() {
        SuiteResult suite = new SuiteResult();
        suite.setSuiteId("contract-replay-suite");
        suite.setTimestamp("2026-03-27T00:00:00");
        suite.setEnvironment("contracttest");

        suite.addContractResult(sampleFileBoundary());
        suite.addContractResult(sampleRunApiBoundary());
        suite.addContractResult(sampleStatusApiBoundary());
        suite.addContractResult(sampleOutputBoundary());

        suite.addReplayResult(sampleRp001());
        suite.addReplayResult(sampleRp002());
        suite.addReplayResult(sampleRp003());

        return suite;
    }

    private static ContractResult sampleFileBoundary() {
        ContractResult result = new ContractResult("ACH-FILE-001", "Inbound File");
        result.addCheck(check(
            "FILE-VALID-001",
            "Canonical inbound fixture satisfies the ACH file contract",
            "Validator returns PASS with no violations",
            "PASS with no violations"));
        result.addCheck(check(
            "FILE-NEG-MISSING-COL",
            "Missing required column is detected",
            "Validator returns FAIL with ACH-FILE-001-COL-MISSING",
            "FAIL with violations: ACH-FILE-001-COL-MISSING"));
        result.addCheck(check(
            "FILE-NEG-LATER-ROW-TYPE",
            "Malformed later rows are validated, not just the first row",
            "Validator returns FAIL with ACH-FILE-001-TYPE-MERCHANT mentioning row 3",
            "FAIL with violations: ACH-FILE-001-TYPE-MERCHANT"));
        result.addCheck(check(
            "FILE-NEG-EXTRA-COL",
            "Extra columns are surfaced as non-breaking drift",
            "Validator returns WARNING with ACH-FILE-001-COL-EXTRA",
            "WARNING with violations: ACH-FILE-001-COL-EXTRA"));
        result.addCheck(check(
            "FILE-NEG-COL-ORDER",
            "Column-order drift is surfaced as a warning",
            "Validator returns WARNING with ACH-FILE-001-COL-ORDER",
            "WARNING with violations: ACH-FILE-001-COL-ORDER"));
        result.setEvaluatedAt("2026-03-27T00:00:01");
        return result;
    }

    private static ContractResult sampleRunApiBoundary() {
        ContractResult result = new ContractResult("API-BATCH-RUN-001", "POST /api/batch/run");
        result.addCheck(check(
            "API-RUN-CLEAN",
            "Happy-path trigger response matches the contract",
            "Scenario clean_trigger satisfies the API contract",
            "status=200, contentType=text/plain;charset=UTF-8, body=\"Pipeline completed successfully\""));
        result.addCheck(check(
            "API-RUN-CONCURRENT",
            "Concurrent trigger conflict response matches the contract",
            "Scenario concurrent_trigger satisfies the API contract",
            "status=409, contentType=text/plain;charset=UTF-8, body=\"Pipeline is already running\""));
        result.addCheck(check(
            "API-RUN-FAILURE",
            "Pipeline failure response matches the contract",
            "Scenario pipeline_failure satisfies the API contract",
            "status=500, contentType=text/plain;charset=UTF-8, body=\"Pipeline failed: forced suite failure\""));
        result.setEvaluatedAt("2026-03-27T00:00:02");
        return result;
    }

    private static ContractResult sampleStatusApiBoundary() {
        ContractResult result = new ContractResult("API-BATCH-STATUS-001", "GET /api/batch/status");
        result.addCheck(check(
            "API-STATUS-IDLE",
            "Idle poll response matches the contract",
            "Scenario pipeline_idle satisfies the API contract",
            "status=200, contentType=text/plain;charset=UTF-8, body=\"idle\""));
        result.addCheck(check(
            "API-STATUS-RUNNING",
            "Running poll response matches the contract",
            "Scenario pipeline_running satisfies the API contract",
            "status=200, contentType=text/plain;charset=UTF-8, body=\"running\""));
        result.setEvaluatedAt("2026-03-27T00:00:03");
        return result;
    }

    private static ContractResult sampleOutputBoundary() {
        ContractResult result = new ContractResult("OUTPUT-RECON-001", "Output State — Reconciliation Invariants");
        result.addCheck(check(
            "OUTPUT-CLEAN-PASS",
            "Clean baseline run satisfies all scoped reconciliation invariants",
            "Scoped invariant check returns PASS with no violations",
            "PASS with no violations"));
        result.addCheck(check(
            "OUTPUT-REJECTED-EXCLUDED",
            "Rejected rows are excluded from INV-003 count and total calculations",
            "Scoped invariant check passes with no INV-003 violations",
            "PASS with no violations"));
        result.addCheck(check(
            "OUTPUT-TAMPER-DETECTED",
            "Tampered staged totals are detected as INV-003-TOTAL",
            "Scoped invariant check fails with INV-003-TOTAL only",
            "FAIL with violations: INV-003-TOTAL"));
        result.addCheck(check(
            "OUTPUT-SCOPED-CLEAN",
            "Historical bad reconciliations do not poison a later scoped clean run",
            "Scoped invariant check returns PASS for the later clean batch_ids",
            "PASS with no violations"));
        result.setEvaluatedAt("2026-03-27T00:00:04");
        return result;
    }

    private static ReplayResult sampleRp001() {
        ReplayResult result = new ReplayResult(
            "RP-001",
            "Baseline happy path — 3 valid records, all posted, reconciliation passes");
        result.setPurpose("Control case: proves the replay harness, Testcontainers setup, DB queries, and reporting all work end to end. If this fails, the environment is broken, not the scenarios.");
        result.setWhatWouldBreakThis("Any harness breakage, fixture drift, database setup issue, or posting/reconciliation regression would cause the baseline control case to fail.");
        result.setStatus(ContractStatus.PASS);
        result.setExpectedSummary("staged=3, posted=3, rejected=0, errors=0, total=31500, reconRows=1");
        result.setActualSummary("staged=3, posted=3, rejected=0, errors=0, total=31500, reconRows=1, countsMatch=true, totalsMatch=true, perRunMatch=n/a");
        result.setActualStagedCount(3);
        result.setActualPostedCount(3);
        result.setActualRejectedCount(0);
        result.setActualErrorCount(0);
        result.setActualTotalPostedCents(31500);
        result.setReconciliationRowCount(1);
        result.setReconciliationCountsMatch(true);
        result.setReconciliationTotalsMatch(true);
        result.setPerRunExpectationsMatch(null);
        result.setReconciliationDetails(List.of(detail(1, 101, 3, 3, 31500, 31500, true, true, true)));
        result.setMismatches(List.of());
        result.setEvaluatedAt("2026-03-27T00:00:05");
        return result;
    }

    private static ReplayResult sampleRp002() {
        ReplayResult result = new ReplayResult(
            "RP-002",
            "Sequential run isolation — same fixture run twice, each run scoped by run.id timestamp");
        result.setPurpose("Proves the reconcile job's WHERE bj.started_at >= ? filter correctly isolates each run's reconciliation. If this filter is removed or broken, run 2 would aggregate both runs' batches and produce a staged_count of 6 instead of 3.");
        result.setWhatWouldBreakThis("Removing or broadening the started_at >= ? filter in ReconcileJobConfig would cause run 2 to include run 1's batches, inflating staged_count and posted_count to 6 in a single reconciliation row.");
        result.setStatus(ContractStatus.PASS);
        result.setExpectedSummary("staged=6, posted=6, rejected=0, errors=0, total=63000, per-run staged=3, per-run posted=3, per-run total=31500, aggregateReconRows=2");
        result.setActualSummary("staged=6, posted=6, rejected=0, errors=0, total=63000, reconRows=2, countsMatch=true, totalsMatch=true, perRunMatch=true");
        result.setActualStagedCount(6);
        result.setActualPostedCount(6);
        result.setActualRejectedCount(0);
        result.setActualErrorCount(0);
        result.setActualTotalPostedCents(63000);
        result.setReconciliationRowCount(2);
        result.setReconciliationCountsMatch(true);
        result.setReconciliationTotalsMatch(true);
        result.setPerRunExpectationsMatch(true);
        result.setReconciliationDetails(List.of(
            detail(1, 102, 3, 3, 31500, 31500, true, true, true),
            detail(2, 103, 3, 3, 31500, 31500, true, true, true)));
        result.setMismatches(List.of());
        result.setEvaluatedAt("2026-03-27T00:00:06");
        return result;
    }

    private static ReplayResult sampleRp003() {
        ReplayResult result = new ReplayResult(
            "RP-003",
            "Validation skip — bad row rejected, pipeline completes, reconciliation excludes rejected row");
        result.setPurpose("Proves that Spring Batch's skip policy tolerates ValidationException, that rejected rows are correctly tracked in batch_job_errors, and that reconciliation's WHERE status='posted' filter excludes rejected records from staged_count.");
        result.setWhatWouldBreakThis("Changing the skip-policy limit to 0, removing ValidationException from the skippable-exception list, or removing the status='posted' filter in ReconcileJobConfig would all cause this test to fail.");
        result.setStatus(ContractStatus.PASS);
        result.setExpectedSummary("staged=3, posted=2, rejected=1, errors=1, total=6500, reconRows=1");
        result.setActualSummary("staged=3, posted=2, rejected=1, errors=1, total=6500, reconRows=1, countsMatch=true, totalsMatch=true, perRunMatch=n/a");
        result.setActualStagedCount(3);
        result.setActualPostedCount(2);
        result.setActualRejectedCount(1);
        result.setActualErrorCount(1);
        result.setActualTotalPostedCents(6500);
        result.setReconciliationRowCount(1);
        result.setReconciliationCountsMatch(true);
        result.setReconciliationTotalsMatch(true);
        result.setPerRunExpectationsMatch(null);
        result.setReconciliationDetails(List.of(detail(1, 104, 2, 2, 6500, 6500, true, true, true)));
        result.setMismatches(List.of());
        result.setEvaluatedAt("2026-03-27T00:00:07");
        return result;
    }

    private static ContractCheck check(String checkId,
                                       String description,
                                       String expectedOutcome,
                                       String actualOutcome) {
        return new ContractCheck(checkId, description, expectedOutcome, actualOutcome, ContractStatus.PASS);
    }

    private static ReplayReconciliationDetail detail(int runNumber,
                                                     int batchId,
                                                     int stagedCount,
                                                     int postedCount,
                                                     long stagedTotalCents,
                                                     long postedTotalCents,
                                                     boolean countsMatch,
                                                     boolean totalsMatch,
                                                     Boolean expectationMatch) {
        ReplayReconciliationDetail detail = new ReplayReconciliationDetail();
        detail.setRunNumber(runNumber);
        detail.setBatchId(batchId);
        detail.setStagedCount(stagedCount);
        detail.setPostedCount(postedCount);
        detail.setStagedTotalCents(stagedTotalCents);
        detail.setPostedTotalCents(postedTotalCents);
        detail.setCountsMatch(countsMatch);
        detail.setTotalsMatch(totalsMatch);
        detail.setExpectationMatch(expectationMatch);
        return detail;
    }

    private static void appendApiScenarioRows(StringBuilder sb, String resourcePath) {
        JsonNode root = readJsonResource(resourcePath);
        for (JsonNode response : root.path("responses")) {
            sb.append("| API scenario | ")
                .append(requiredText(response, "scenario", resourcePath))
                .append(" | ")
                .append(escape(requiredText(response, "description", resourcePath)))
                .append(" | ")
                .append(escape(requiredText(response, "guardsAgainst", resourcePath)))
                .append(" |\n");
        }
    }

    private static void appendInvariantRows(StringBuilder sb, String resourcePath) {
        JsonNode root = readJsonResource(resourcePath);
        for (JsonNode invariant : root.path("invariants")) {
            sb.append("| Output invariant | ")
                .append(requiredText(invariant, "id", resourcePath))
                .append(" | ")
                .append(escape(requiredText(invariant, "description", resourcePath)))
                .append(" | ")
                .append(escape(requiredText(invariant, "guardsAgainst", resourcePath)))
                .append(" |\n");
        }
    }

    private static void appendReplayRows(StringBuilder sb, List<String> resources) {
        for (String resource : resources) {
            ReplayExpectation expectation = ReplayExpectationLoader.load(resource);
            sb.append("| Replay scenario | ")
                .append(expectation.getScenarioId())
                .append(" | ")
                .append(escape(expectation.getDescription()))
                .append(" | ")
                .append(escape(expectation.getWhatWouldBreakThis()))
                .append(" |\n");
        }
    }

    private static JsonNode readJsonResource(String resourcePath) {
        try (InputStream is = PortfolioShowcaseArtifacts.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Classpath resource not found: " + resourcePath);
            }
            return MAPPER.readTree(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read showcase metadata from " + resourcePath, e);
        }
    }

    private static String requiredText(JsonNode node, String field, String resourcePath) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required showcase metadata field '" + field + "' in " + resourcePath);
        }
        return value;
    }

    private static String escape(String value) {
        return value.replace("|", "\\|");
    }
}

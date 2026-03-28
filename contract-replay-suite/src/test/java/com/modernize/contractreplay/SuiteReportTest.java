package com.modernize.contractreplay;

import com.modernize.bankbatch.BankBatchApplication;
import com.modernize.bankbatch.BatchPipelineService;
import com.modernize.contractreplay.contract.ApiContractVerifier;
import com.modernize.contractreplay.contract.FileContractValidator;
import com.modernize.contractreplay.contract.OutputStateInvariantChecker;
import com.modernize.contractreplay.model.ContractCheck;
import com.modernize.contractreplay.model.ContractResult;
import com.modernize.contractreplay.model.ContractStatus;
import com.modernize.contractreplay.model.ContractViolation;
import com.modernize.contractreplay.model.ReplayReconciliationDetail;
import com.modernize.contractreplay.model.ReplayResult;
import com.modernize.contractreplay.model.SuiteResult;
import com.modernize.contractreplay.replay.ReplayRun;
import com.modernize.contractreplay.replay.ReplayRunner;
import com.modernize.contractreplay.report.ReportWriter;
import com.modernize.contractreplay.support.ReplayExpectation;
import com.modernize.contractreplay.support.ReplayExpectationLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBatchTest
@SpringBootTest(
    classes = BankBatchApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("contracttest")
class SuiteReportTest {

    private static final String RUN_CONTRACT = "contracts/api/batch-run-contract.json";
    private static final String STATUS_CONTRACT = "contracts/api/batch-status-contract.json";
    private static final ReplayExpectation RP001 =
        ReplayExpectationLoader.load("fixtures/rp-001-expected-outcome.json");
    private static final ReplayExpectation RP002 =
        ReplayExpectationLoader.load("fixtures/rp-002-expected-outcome.json");
    private static final ReplayExpectation RP003 =
        ReplayExpectationLoader.load("fixtures/rp-003-expected-outcome.json");

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired private JobRepositoryTestUtils jobRepositoryTestUtils;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TestRestTemplate restTemplate;

    @Autowired @Qualifier("loadTransactionsJob") private Job loadTransactionsJob;
    @Autowired @Qualifier("validateTransactionsJob") private Job validateTransactionsJob;
    @Autowired @Qualifier("postTransactionsJob") private Job postTransactionsJob;
    @Autowired @Qualifier("reconcileJob") private Job reconcileJob;

    @MockitoBean
    private BatchPipelineService mockPipelineService;

    @BeforeEach
    void cleanState() {
        ReplayRunner.cleanState(jobRepositoryTestUtils, jdbcTemplate);
    }

    @Test
    void generateFullSuiteReport() throws Exception {
        SuiteResult suite = new SuiteResult();

        suite.addContractResult(buildFileBoundaryEvidence());
        suite.addContractResult(buildRunApiBoundaryEvidence());
        suite.addContractResult(buildStatusApiBoundaryEvidence());
        suite.addContractResult(buildOutputBoundaryEvidence());

        suite.addReplayResult(runRp001Scenario());
        suite.addReplayResult(runRp002Scenario());
        suite.addReplayResult(runRp003Scenario());

        ReportWriter writer = new ReportWriter();
        Path basePath = writer.write(suite);

        Path jsonFile = Path.of(basePath + ".json");
        Path mdFile = Path.of(basePath + ".md");
        assertTrue(Files.exists(jsonFile), "JSON report should exist at " + jsonFile);
        assertTrue(Files.exists(mdFile), "Markdown report should exist at " + mdFile);

        assertEquals(ContractStatus.PASS, suite.getOverallStatus(),
            "Suite should be fully green. Contract results: " + suite.getContractResults()
                + " — Replay results: " + suite.getReplayResults());
        assertEquals(4, suite.getContractResults().size(),
            "Expected 4 contract results: file, API run, API status, output invariants");
        assertEquals(3, suite.getReplayResults().size(),
            "Expected 3 replay results: RP-001, RP-002, RP-003");
    }

    private ContractResult buildFileBoundaryEvidence() throws IOException {
        ContractResult boundary = new ContractResult("ACH-FILE-001", "Inbound File");

        ContractResult validResult = FileContractValidator.validate(readClasspathResource(RP001.getFixtureFile()));
        recordBoundaryCheck(
            boundary,
            "FILE-VALID-001",
            "Canonical inbound fixture satisfies the ACH file contract",
            "Validator returns PASS with no violations",
            summarizeContractResult(validResult),
            validResult.getOverallStatus() == ContractStatus.PASS,
            validResult);

        ContractResult missingColumn = FileContractValidator.validate(
            "account_id,merchant_id,direction,amount_cents\n" +
            "2001,3001,D,5000\n");
        recordBoundaryCheck(
            boundary,
            "FILE-NEG-MISSING-COL",
            "Missing required column is detected",
            "Validator returns FAIL with ACH-FILE-001-COL-MISSING",
            summarizeContractResult(missingColumn),
            missingColumn.getOverallStatus() == ContractStatus.FAIL
                && hasRule(missingColumn, "ACH-FILE-001-COL-MISSING"),
            missingColumn);

        ContractResult laterRowInvalid = FileContractValidator.validate(
            "account_id,merchant_id,direction,amount_cents,txn_date\n" +
            "2001,3001,D,5000,2025-03-10\n" +
            "2002,abc,C,1500,2025-03-10\n");
        recordBoundaryCheck(
            boundary,
            "FILE-NEG-LATER-ROW-TYPE",
            "Malformed later rows are validated, not just the first row",
            "Validator returns FAIL with ACH-FILE-001-TYPE-MERCHANT mentioning row 3",
            summarizeContractResult(laterRowInvalid),
            laterRowInvalid.getOverallStatus() == ContractStatus.FAIL
                && hasRule(laterRowInvalid, "ACH-FILE-001-TYPE-MERCHANT")
                && mentionsRow(laterRowInvalid, 3),
            laterRowInvalid);

        ContractResult extraColumn = FileContractValidator.validate(
            "account_id,merchant_id,direction,amount_cents,txn_date,notes\n" +
            "2001,3001,D,5000,2025-03-10,ok\n");
        recordBoundaryCheck(
            boundary,
            "FILE-NEG-EXTRA-COL",
            "Extra columns are surfaced as non-breaking drift",
            "Validator returns WARNING with ACH-FILE-001-COL-EXTRA",
            summarizeContractResult(extraColumn),
            extraColumn.getOverallStatus() == ContractStatus.WARNING
                && hasRule(extraColumn, "ACH-FILE-001-COL-EXTRA"),
            extraColumn);

        ContractResult reorderedColumns = FileContractValidator.validate(
            "account_id,merchant_id,direction,txn_date,amount_cents\n" +
            "2001,3001,D,2025-03-10,5000\n");
        recordBoundaryCheck(
            boundary,
            "FILE-NEG-COL-ORDER",
            "Column-order drift is surfaced as a warning",
            "Validator returns WARNING with ACH-FILE-001-COL-ORDER",
            summarizeContractResult(reorderedColumns),
            reorderedColumns.getOverallStatus() == ContractStatus.WARNING
                && hasRule(reorderedColumns, "ACH-FILE-001-COL-ORDER"),
            reorderedColumns);

        return boundary;
    }

    private ContractResult buildRunApiBoundaryEvidence() throws Exception {
        ApiContractVerifier verifier = new ApiContractVerifier(RUN_CONTRACT);
        ContractResult boundary = new ContractResult("API-BATCH-RUN-001", "POST /api/batch/run");

        reset(mockPipelineService);
        when(mockPipelineService.isRunning()).thenReturn(false);
        doNothing().when(mockPipelineService).run();
        ResponseEntity<String> cleanTrigger = restTemplate.postForEntity("/api/batch/run", null, String.class);
        ContractResult cleanResult = verifier.verify(
            "clean_trigger",
            cleanTrigger.getStatusCode().value(),
            cleanTrigger.getHeaders().getFirst("Content-Type"),
            cleanTrigger.getBody());
        recordBoundaryCheck(
            boundary,
            "API-RUN-CLEAN",
            "Happy-path trigger response matches the contract",
            "Scenario clean_trigger satisfies the API contract",
            responseSummary(cleanTrigger),
            cleanResult.getOverallStatus() == ContractStatus.PASS,
            cleanResult);

        reset(mockPipelineService);
        when(mockPipelineService.isRunning()).thenReturn(false);
        doThrow(new IllegalStateException("Pipeline is already running")).when(mockPipelineService).run();
        ResponseEntity<String> concurrentTrigger = restTemplate.postForEntity("/api/batch/run", null, String.class);
        ContractResult concurrentResult = verifier.verify(
            "concurrent_trigger",
            concurrentTrigger.getStatusCode().value(),
            concurrentTrigger.getHeaders().getFirst("Content-Type"),
            concurrentTrigger.getBody());
        recordBoundaryCheck(
            boundary,
            "API-RUN-CONCURRENT",
            "Concurrent trigger conflict response matches the contract",
            "Scenario concurrent_trigger satisfies the API contract",
            responseSummary(concurrentTrigger),
            concurrentResult.getOverallStatus() == ContractStatus.PASS,
            concurrentResult);

        reset(mockPipelineService);
        when(mockPipelineService.isRunning()).thenReturn(false);
        doThrow(new RuntimeException("forced suite failure")).when(mockPipelineService).run();
        ResponseEntity<String> pipelineFailure = restTemplate.postForEntity("/api/batch/run", null, String.class);
        ContractResult failureResult = verifier.verify(
            "pipeline_failure",
            pipelineFailure.getStatusCode().value(),
            pipelineFailure.getHeaders().getFirst("Content-Type"),
            pipelineFailure.getBody());
        recordBoundaryCheck(
            boundary,
            "API-RUN-FAILURE",
            "Pipeline failure response matches the contract",
            "Scenario pipeline_failure satisfies the API contract",
            responseSummary(pipelineFailure),
            failureResult.getOverallStatus() == ContractStatus.PASS,
            failureResult);

        return boundary;
    }

    private ContractResult buildStatusApiBoundaryEvidence() {
        ApiContractVerifier verifier = new ApiContractVerifier(STATUS_CONTRACT);
        ContractResult boundary = new ContractResult("API-BATCH-STATUS-001", "GET /api/batch/status");

        reset(mockPipelineService);
        when(mockPipelineService.isRunning()).thenReturn(false);
        ResponseEntity<String> idle = restTemplate.getForEntity("/api/batch/status", String.class);
        ContractResult idleResult = verifier.verify(
            "pipeline_idle",
            idle.getStatusCode().value(),
            idle.getHeaders().getFirst("Content-Type"),
            idle.getBody());
        recordBoundaryCheck(
            boundary,
            "API-STATUS-IDLE",
            "Idle poll response matches the contract",
            "Scenario pipeline_idle satisfies the API contract",
            responseSummary(idle),
            idleResult.getOverallStatus() == ContractStatus.PASS,
            idleResult);

        reset(mockPipelineService);
        when(mockPipelineService.isRunning()).thenReturn(true);
        ResponseEntity<String> running = restTemplate.getForEntity("/api/batch/status", String.class);
        ContractResult runningResult = verifier.verify(
            "pipeline_running",
            running.getStatusCode().value(),
            running.getHeaders().getFirst("Content-Type"),
            running.getBody());
        recordBoundaryCheck(
            boundary,
            "API-STATUS-RUNNING",
            "Running poll response matches the contract",
            "Scenario pipeline_running satisfies the API contract",
            responseSummary(running),
            runningResult.getOverallStatus() == ContractStatus.PASS,
            runningResult);

        return boundary;
    }

    private ContractResult buildOutputBoundaryEvidence() throws Exception {
        ContractResult boundary = new ContractResult("OUTPUT-RECON-001", "Output State — Reconciliation Invariants");

        ReplayRunner.cleanState(jobRepositoryTestUtils, jdbcTemplate);
        ReplayRun cleanRun = runPipeline(RP001.getFixtureFile());
        ContractResult cleanResult = new OutputStateInvariantChecker(jdbcTemplate, cleanRun.batchIds()).check();
        recordBoundaryCheck(
            boundary,
            "OUTPUT-CLEAN-PASS",
            "Clean baseline run satisfies all scoped reconciliation invariants",
            "Scoped invariant check returns PASS with no violations",
            summarizeContractResult(cleanResult),
            cleanResult.getOverallStatus() == ContractStatus.PASS && cleanResult.getViolations().isEmpty(),
            cleanResult);

        ReplayRunner.cleanState(jobRepositoryTestUtils, jdbcTemplate);
        ReplayRun rejectionRun = runPipeline(RP003.getFixtureFile());
        ContractResult rejectionResult = new OutputStateInvariantChecker(jdbcTemplate, rejectionRun.batchIds()).check();
        recordBoundaryCheck(
            boundary,
            "OUTPUT-REJECTED-EXCLUDED",
            "Rejected rows are excluded from INV-003 count and total calculations",
            "Scoped invariant check passes with no INV-003 violations",
            summarizeContractResult(rejectionResult),
            rejectionResult.getOverallStatus() == ContractStatus.PASS
                && !hasRule(rejectionResult, "INV-003-COUNT")
                && !hasRule(rejectionResult, "INV-003-TOTAL"),
            rejectionResult);

        ReplayRunner.cleanState(jobRepositoryTestUtils, jdbcTemplate);
        ReplayRun tamperedRun = runPipeline(RP001.getFixtureFile());
        jdbcTemplate.update(
            "UPDATE bank.batch_reconciliations " +
            "SET staged_total_cents = staged_total_cents + 1 " +
            "WHERE batch_id = ?",
            tamperedRun.batchIds().getFirst());
        ContractResult tamperedResult = new OutputStateInvariantChecker(jdbcTemplate, tamperedRun.batchIds()).check();
        recordBoundaryCheck(
            boundary,
            "OUTPUT-TAMPER-DETECTED",
            "Tampered staged totals are detected as INV-003-TOTAL",
            "Scoped invariant check fails with INV-003-TOTAL only",
            summarizeContractResult(tamperedResult),
            hasRule(tamperedResult, "INV-003-TOTAL") && !hasRule(tamperedResult, "INV-003-COUNT"),
            tamperedResult);

        ReplayRunner.cleanState(jobRepositoryTestUtils, jdbcTemplate);
        ReplayRun historicalBadRun = runPipeline(RP001.getFixtureFile());
        jdbcTemplate.update(
            "UPDATE bank.batch_reconciliations " +
            "SET staged_total_cents = staged_total_cents + 1 " +
            "WHERE batch_id = ?",
            historicalBadRun.batchIds().getFirst());
        ReplayRun laterCleanRun = runPipeline(RP001.getFixtureFile());
        ContractResult scopedCleanResult = new OutputStateInvariantChecker(jdbcTemplate, laterCleanRun.batchIds()).check();
        recordBoundaryCheck(
            boundary,
            "OUTPUT-SCOPED-CLEAN",
            "Historical bad reconciliations do not poison a later scoped clean run",
            "Scoped invariant check returns PASS for the later clean batch_ids",
            summarizeContractResult(scopedCleanResult),
            scopedCleanResult.getOverallStatus() == ContractStatus.PASS && scopedCleanResult.getViolations().isEmpty(),
            scopedCleanResult);

        return boundary;
    }

    private ReplayResult runRp001Scenario() throws Exception {
        ReplayRunner.cleanState(jobRepositoryTestUtils, jdbcTemplate);
        ReplayRun run = runPipeline(RP001.getFixtureFile());
        return captureReplayResult(RP001, run);
    }

    private ReplayResult runRp002Scenario() throws Exception {
        ReplayRunner.cleanState(jobRepositoryTestUtils, jdbcTemplate);
        List<ReplayRun> runs = new ArrayList<>();
        for (int i = 0; i < RP002.getRunsSubmitted(); i++) {
            runs.add(runPipeline(RP002.getFixtureFile()));
        }
        return captureReplayResult(RP002, runs.toArray(ReplayRun[]::new));
    }

    private ReplayResult runRp003Scenario() throws Exception {
        ReplayRunner.cleanState(jobRepositoryTestUtils, jdbcTemplate);
        ReplayRun run = runPipeline(RP003.getFixtureFile());
        return captureReplayResult(RP003, run);
    }

    private ReplayRun runPipeline(String fixtureFile) throws Exception {
        return ReplayRunner.runFullPipeline(
            jobLauncherTestUtils,
            loadTransactionsJob,
            validateTransactionsJob,
            postTransactionsJob,
            reconcileJob,
            jdbcTemplate,
            fixtureFile);
    }

    private ReplayResult captureReplayResult(ReplayExpectation expectation, ReplayRun... runs) {
        ReplayResult result = new ReplayResult(expectation.getScenarioId(), expectation.getDescription());
        result.setPurpose(expectation.getPurpose());
        result.setWhatWouldBreakThis(expectation.getWhatWouldBreakThis());

        List<ReplayRun> scenarioRuns = Arrays.asList(runs);
        List<String> mismatches = new ArrayList<>();

        Integer staged = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.staged_transactions", Integer.class);
        result.setActualStagedCount(staged != null ? staged : 0);

        Integer posted = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.transactions", Integer.class);
        result.setActualPostedCount(posted != null ? posted : 0);

        Integer rejected = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.staged_transactions WHERE status = 'rejected'", Integer.class);
        result.setActualRejectedCount(rejected != null ? rejected : 0);

        Integer errors = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_job_errors", Integer.class);
        result.setActualErrorCount(errors != null ? errors : 0);

        Long total = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM bank.transactions", Long.class);
        result.setActualTotalPostedCents(total != null ? total : 0L);

        List<ReplayReconciliationDetail> details = loadReconciliationDetails(scenarioRuns, expectation);
        result.setReconciliationDetails(details);
        result.setReconciliationRowCount(details.size());
        result.setReconciliationCountsMatch(details.stream().allMatch(ReplayReconciliationDetail::isCountsMatch));
        result.setReconciliationTotalsMatch(details.stream().allMatch(ReplayReconciliationDetail::isTotalsMatch));

        compareInt("staged count", expectation.getExpectedStagedCount(), result.getActualStagedCount(), mismatches);
        compareInt("posted count", expectation.getExpectedPostedCount(), result.getActualPostedCount(), mismatches);
        compareInt("rejected count", expectation.getExpectedRejectedCount(), result.getActualRejectedCount(), mismatches);
        compareInt("error count", expectation.getExpectedErrorCount(), result.getActualErrorCount(), mismatches);
        compareLong("total posted cents", expectation.getExpectedTotalPostedCents(), result.getActualTotalPostedCents(), mismatches);

        ReplayExpectation.ReconciliationExpectation singleRun = expectation.getExpectedReconciliation();
        if (singleRun != null) {
            List<ReplayReconciliationDetail> runDetails = scenarioRuns.isEmpty()
                ? List.of()
                : details.stream().filter(detail -> detail.getRunNumber() == 1).toList();

            compareInt("reconciliation row count", singleRun.getRowCount(), runDetails.size(), mismatches);
            compareBoolean("reconciliation counts_match", singleRun.getCountsMatch(), result.isReconciliationCountsMatch(), mismatches);
            compareBoolean("reconciliation totals_match", singleRun.getTotalsMatch(), result.isReconciliationTotalsMatch(), mismatches);

            if (!runDetails.isEmpty()) {
                ReplayReconciliationDetail row = runDetails.getFirst();
                compareInt("reconciliation staged_count", singleRun.getStagedCount(), row.getStagedCount(), mismatches);
                compareInt("reconciliation posted_count", singleRun.getPostedCount(), row.getPostedCount(), mismatches);
                compareLong("reconciliation staged_total_cents", singleRun.getStagedTotalCents(), row.getStagedTotalCents(), mismatches);
                compareLong("reconciliation posted_total_cents", singleRun.getPostedTotalCents(), row.getPostedTotalCents(), mismatches);
            }
        }

        ReplayExpectation.PerRunExpectation perRun = expectation.getExpectedPerRun();
        if (perRun != null) {
            boolean perRunMatches = details.stream().allMatch(detail -> Boolean.TRUE.equals(detail.getExpectationMatch()));
            result.setPerRunExpectationsMatch(perRunMatches);
            compareBoolean("per-run reconciliation expectations", Boolean.TRUE, perRunMatches, mismatches);
        }

        ReplayExpectation.AggregateExpectation aggregate = expectation.getExpectedAfterBothRuns();
        if (aggregate != null) {
            compareInt("aggregate staged count", aggregate.getTotalStagedTransactions(), result.getActualStagedCount(), mismatches);
            compareInt("aggregate posted count", aggregate.getTotalPostedTransactions(), result.getActualPostedCount(), mismatches);
            compareInt("aggregate reconciliation row count", aggregate.getReconciliationRowCount(), result.getReconciliationRowCount(), mismatches);
            compareBoolean("aggregate eachRowCountsMatch", aggregate.getEachRowCountsMatch(), result.isReconciliationCountsMatch(), mismatches);
            compareBoolean("aggregate eachRowTotalsMatch", aggregate.getEachRowTotalsMatch(), result.isReconciliationTotalsMatch(), mismatches);
        }

        result.setExpectedSummary(buildExpectedSummary(expectation));
        result.setActualSummary(buildActualSummary(result));
        result.setMismatches(mismatches);
        result.setStatus(mismatches.isEmpty() ? ContractStatus.PASS : ContractStatus.FAIL);
        return result;
    }

    private List<ReplayReconciliationDetail> loadReconciliationDetails(List<ReplayRun> runs,
                                                                       ReplayExpectation expectation) {
        List<ReplayReconciliationDetail> details = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            ReplayRun run = runs.get(i);
            int runNumber = i + 1;
            List<ReplayReconciliationDetail> rows = jdbcTemplate.query(
                "SELECT batch_id, staged_count, posted_count, staged_total_cents, posted_total_cents, counts_match, totals_match " +
                "FROM bank.batch_reconciliations " +
                "WHERE batch_id IN (" + placeholders(run.batchIds().size()) + ") " +
                "ORDER BY batch_id",
                (rs, rowNum) -> {
                    ReplayReconciliationDetail detail = new ReplayReconciliationDetail();
                    detail.setRunNumber(runNumber);
                    detail.setBatchId(rs.getInt("batch_id"));
                    detail.setStagedCount(rs.getInt("staged_count"));
                    detail.setPostedCount(rs.getInt("posted_count"));
                    detail.setStagedTotalCents(rs.getLong("staged_total_cents"));
                    detail.setPostedTotalCents(rs.getLong("posted_total_cents"));
                    detail.setCountsMatch(rs.getBoolean("counts_match"));
                    detail.setTotalsMatch(rs.getBoolean("totals_match"));
                    detail.setExpectationMatch(matchDetailExpectation(detail, expectation));
                    return detail;
                },
                run.batchIds().toArray());
            details.addAll(rows);
        }
        return details;
    }

    private Boolean matchDetailExpectation(ReplayReconciliationDetail detail, ReplayExpectation expectation) {
        ReplayExpectation.PerRunExpectation perRun = expectation.getExpectedPerRun();
        if (perRun != null) {
            return matchesInt(perRun.getStagedCount(), detail.getStagedCount())
                && matchesInt(perRun.getPostedCount(), detail.getPostedCount())
                && matchesLong(perRun.getStagedTotalCents(), detail.getStagedTotalCents())
                && matchesLong(perRun.getPostedTotalCents(), detail.getPostedTotalCents())
                && matchesBoolean(perRun.getCountsMatch(), detail.isCountsMatch())
                && matchesBoolean(perRun.getTotalsMatch(), detail.isTotalsMatch());
        }

        ReplayExpectation.ReconciliationExpectation singleRun = expectation.getExpectedReconciliation();
        if (singleRun != null) {
            return matchesInt(singleRun.getStagedCount(), detail.getStagedCount())
                && matchesInt(singleRun.getPostedCount(), detail.getPostedCount())
                && matchesLong(singleRun.getStagedTotalCents(), detail.getStagedTotalCents())
                && matchesLong(singleRun.getPostedTotalCents(), detail.getPostedTotalCents())
                && matchesBoolean(singleRun.getCountsMatch(), detail.isCountsMatch())
                && matchesBoolean(singleRun.getTotalsMatch(), detail.isTotalsMatch());
        }

        return null;
    }

    private String buildExpectedSummary(ReplayExpectation expectation) {
        StringBuilder summary = new StringBuilder();
        summary.append("staged=").append(expectation.getExpectedStagedCount())
            .append(", posted=").append(expectation.getExpectedPostedCount())
            .append(", rejected=").append(expectation.getExpectedRejectedCount())
            .append(", errors=").append(expectation.getExpectedErrorCount())
            .append(", total=").append(expectation.getExpectedTotalPostedCents());

        if (expectation.getExpectedPerRun() != null) {
            summary.append(", per-run staged=").append(expectation.getExpectedPerRun().getStagedCount())
                .append(", per-run posted=").append(expectation.getExpectedPerRun().getPostedCount())
                .append(", per-run posted-total=").append(expectation.getExpectedPerRun().getPostedTotalCents());
            if (expectation.getExpectedPerRun().getStagedTotalCents() != null) {
                summary.append(", per-run staged-total=").append(expectation.getExpectedPerRun().getStagedTotalCents());
            }
        }

        if (expectation.getExpectedReconciliation() != null) {
            summary.append(", reconRows=").append(expectation.getExpectedReconciliation().getRowCount());
        }

        if (expectation.getExpectedAfterBothRuns() != null) {
            summary.append(", aggregateReconRows=").append(expectation.getExpectedAfterBothRuns().getReconciliationRowCount());
        }

        return summary.toString();
    }

    private String buildActualSummary(ReplayResult result) {
        return "staged=" + result.getActualStagedCount()
            + ", posted=" + result.getActualPostedCount()
            + ", rejected=" + result.getActualRejectedCount()
            + ", errors=" + result.getActualErrorCount()
            + ", total=" + result.getActualTotalPostedCents()
            + ", reconRows=" + result.getReconciliationRowCount()
            + ", countsMatch=" + result.isReconciliationCountsMatch()
            + ", totalsMatch=" + result.isReconciliationTotalsMatch()
            + ", perRunMatch=" + (result.getPerRunExpectationsMatch() == null ? "n/a" : result.getPerRunExpectationsMatch());
    }

    private void compareInt(String label, Integer expected, int actual, List<String> mismatches) {
        if (expected != null && expected != actual) {
            mismatches.add(label + " expected " + expected + " but was " + actual);
        }
    }

    private void compareLong(String label, Long expected, long actual, List<String> mismatches) {
        if (expected != null && expected != actual) {
            mismatches.add(label + " expected " + expected + " but was " + actual);
        }
    }

    private void compareBoolean(String label, Boolean expected, boolean actual, List<String> mismatches) {
        if (expected != null && expected != actual) {
            mismatches.add(label + " expected " + expected + " but was " + actual);
        }
    }

    private boolean matchesInt(Integer expected, int actual) {
        return expected == null || expected == actual;
    }

    private boolean matchesLong(Long expected, long actual) {
        return expected == null || expected == actual;
    }

    private boolean matchesBoolean(Boolean expected, boolean actual) {
        return expected == null || expected == actual;
    }

    private void recordBoundaryCheck(ContractResult boundary,
                                     String checkId,
                                     String description,
                                     String expectedOutcome,
                                     String actualOutcome,
                                     boolean passed,
                                     ContractResult unexpectedResult) {
        ContractStatus status = passed ? ContractStatus.PASS : unexpectedResult.getOverallStatus();
        boundary.addCheck(new ContractCheck(checkId, description, expectedOutcome, actualOutcome, status));

        if (!passed) {
            if (unexpectedResult.getViolations().isEmpty()) {
                boundary.addViolation(new ContractViolation(checkId, description, status, expectedOutcome, actualOutcome));
            } else {
                for (ContractViolation violation : unexpectedResult.getViolations()) {
                    boundary.addViolation(violation);
                }
            }
        }
    }

    private boolean hasRule(ContractResult result, String ruleId) {
        return result.getViolations().stream().anyMatch(v -> v.getRuleId().equals(ruleId));
    }

    private boolean mentionsRow(ContractResult result, int rowNumber) {
        String marker = "row " + rowNumber;
        return result.getViolations().stream().anyMatch(v ->
            v.getDescription().contains(marker) || v.getActual().contains(marker));
    }

    private String summarizeContractResult(ContractResult result) {
        if (result.getViolations().isEmpty()) {
            return result.getOverallStatus() + " with no violations";
        }
        String rules = result.getViolations().stream()
            .map(ContractViolation::getRuleId)
            .distinct()
            .collect(Collectors.joining(", "));
        return result.getOverallStatus() + " with violations: " + rules;
    }

    private String responseSummary(ResponseEntity<String> response) {
        return "status=" + response.getStatusCode().value()
            + ", contentType=" + response.getHeaders().getFirst("Content-Type")
            + ", body=\"" + response.getBody() + "\"";
    }

    private String readClasspathResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Classpath resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }
}

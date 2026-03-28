package com.modernize.contractreplay.replay;

import com.modernize.contractreplay.AbstractContractReplayTest;
import com.modernize.contractreplay.support.ReplayExpectation;
import com.modernize.contractreplay.support.ReplayExpectationLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RP-002 — Run isolation.
 *
 * The same fixture is processed twice through the full pipeline. ReplayRunner
 * allocates a distinct monotonic run.id for each call, which means Spring Batch
 * treats the two executions as separate job instances.
 * The validate and post jobs filter by status='staged' / status='validated', so
 * run 2 only picks up records staged in run 2 — run 1's posted records are
 * invisible to it.
 *
 * This test proves the isolation guarantee: two sequential batches against the
 * same input file produce two independent reconciliation rows, no cross-run
 * contamination, and a clean aggregate state.
 *
 * Fixture: fixtures/rp-001-baseline.csv (same file used for both runs)
 *   3 records per run, all valid.
 *
 * Expected after both runs:
 *   staged_transactions = 6  (3 per run)
 *   transactions        = 6  (all posted)
 *   batch_reconciliations = 2  (one per run)
 *   each recon row: counts_match=true, totals_match=true
 *   batch_job_errors    = 0
 */
class Rp002RunIsolationTest extends AbstractContractReplayTest {

    private static final ReplayExpectation EXPECTATION =
        ReplayExpectationLoader.load("fixtures/rp-002-expected-outcome.json");

    private ReplayRun firstRun;
    private ReplayRun secondRun;

    @BeforeEach
    void runPipelineTwice() throws Exception {
        firstRun = ReplayRunner.runFullPipeline(
                jobLauncherTestUtils,
                loadTransactionsJob,
                validateTransactionsJob,
                postTransactionsJob,
                reconcileJob,
                jdbcTemplate,
                EXPECTATION.getFixtureFile());

        secondRun = ReplayRunner.runFullPipeline(
                jobLauncherTestUtils,
                loadTransactionsJob,
                validateTransactionsJob,
                postTransactionsJob,
                reconcileJob,
                jdbcTemplate,
                EXPECTATION.getFixtureFile());
    }

    @Test
    void twoBatchesShouldExist() {
        // Two independent load operations → two transaction_batches rows.
        // This is the core isolation guarantee: each run is a separate batch.
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.transaction_batches", Integer.class);
        assertEquals(2, count);
    }

    @Test
    void bothRunsCountsShouldMatch() {
        int mismatched = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations WHERE counts_match = false",
            Integer.class);
        assertEquals(0, mismatched, "Expected counts_match=true for all reconciliation rows");
    }

    @Test
    void bothRunsTotalsShouldMatch() {
        int mismatched = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations WHERE totals_match = false",
            Integer.class);
        assertEquals(0, mismatched, "Expected totals_match=true for all reconciliation rows");
    }

    @Test
    void totalStagedCountShouldBeSix() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.staged_transactions", Integer.class);
        assertEquals(EXPECTATION.getExpectedAfterBothRuns().getTotalStagedTransactions(), count);
    }

    @Test
    void totalPostedCountShouldBeSix() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.transactions", Integer.class);
        assertEquals(EXPECTATION.getExpectedAfterBothRuns().getTotalPostedTransactions(), count);
    }

    @Test
    void noErrorsShouldExist() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_job_errors", Integer.class);
        assertEquals(0, count);
    }

    @Test
    void reconciliationRowCountShouldBeTwo() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations", Integer.class);
        assertEquals(EXPECTATION.getExpectedAfterBothRuns().getReconciliationRowCount(), count);
    }

    @Test
    void firstRunReconciliationShouldMatchPerRunExpectation() {
        assertPerRunExpectation(firstRun);
    }

    @Test
    void secondRunReconciliationShouldMatchPerRunExpectation() {
        assertPerRunExpectation(secondRun);
    }

    private void assertPerRunExpectation(ReplayRun run) {
        ReplayExpectation.PerRunExpectation perRun = EXPECTATION.getExpectedPerRun();

        assertNotNull(perRun, "RP-002 fixture must declare expectedPerRun");
        assertEquals(1, run.batchIds().size(), "Each replay run should load exactly one batch");

        Integer batchId = run.batchIds().getFirst();
        int rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations WHERE batch_id = ?",
            Integer.class,
            batchId);
        assertEquals(1, rowCount, "Each run should produce exactly one reconciliation row");

        Integer stagedCount = jdbcTemplate.queryForObject(
            "SELECT staged_count FROM bank.batch_reconciliations WHERE batch_id = ?",
            Integer.class,
            batchId);
        Integer postedCount = jdbcTemplate.queryForObject(
            "SELECT posted_count FROM bank.batch_reconciliations WHERE batch_id = ?",
            Integer.class,
            batchId);
        Long stagedTotal = jdbcTemplate.queryForObject(
            "SELECT staged_total_cents FROM bank.batch_reconciliations WHERE batch_id = ?",
            Long.class,
            batchId);
        Long postedTotal = jdbcTemplate.queryForObject(
            "SELECT posted_total_cents FROM bank.batch_reconciliations WHERE batch_id = ?",
            Long.class,
            batchId);
        Boolean countsMatch = jdbcTemplate.queryForObject(
            "SELECT counts_match FROM bank.batch_reconciliations WHERE batch_id = ?",
            Boolean.class,
            batchId);
        Boolean totalsMatch = jdbcTemplate.queryForObject(
            "SELECT totals_match FROM bank.batch_reconciliations WHERE batch_id = ?",
            Boolean.class,
            batchId);

        assertEquals(perRun.getStagedCount(), stagedCount);
        assertEquals(perRun.getPostedCount(), postedCount);
        assertEquals(perRun.getStagedTotalCents(), stagedTotal);
        assertEquals(perRun.getPostedTotalCents(), postedTotal);
        assertEquals(stagedTotal, postedTotal, "staged_total_cents and posted_total_cents should match in a clean run");
        assertEquals(perRun.getCountsMatch(), countsMatch);
        assertEquals(perRun.getTotalsMatch(), totalsMatch);
    }
}

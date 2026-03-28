package com.modernize.contractreplay.replay;

import com.modernize.contractreplay.AbstractContractReplayTest;
import com.modernize.contractreplay.support.ReplayExpectation;
import com.modernize.contractreplay.support.ReplayExpectationLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RP-001 — Baseline control case.
 *
 * Three valid records processed through the full pipeline with no rejected rows,
 * no errors, and a clean reconciliation. This is the control case: if any of
 * these assertions fail, the environment or test setup is broken — not a scenario
 * under test. All other replay tests assume RP-001 passes.
 *
 * Fixture: fixtures/rp-001-baseline.csv
 *   account_id  merchant_id  direction  amount_cents  txn_date
 *   2001        3001         D          5000          2025-03-10
 *   2002        3002         C          1500          2025-03-10
 *   2003        (null)       C          25000         2025-03-10
 *
 * Expected: staged=3, posted=3, rejected=0, errors=0, total=31500 cents,
 *           counts_match=true, totals_match=true, one reconciliation row.
 */
class Rp001BaselineTest extends AbstractContractReplayTest {

    private static final ReplayExpectation EXPECTATION =
        ReplayExpectationLoader.load("fixtures/rp-001-expected-outcome.json");

    private ReplayRun run;

    @BeforeEach
    void runPipeline() throws Exception {
        run = ReplayRunner.runFullPipeline(
            jobLauncherTestUtils,
            loadTransactionsJob,
            validateTransactionsJob,
            postTransactionsJob,
            reconcileJob,
            jdbcTemplate,
            EXPECTATION.getFixtureFile());
    }

    @Test
    void stagedCount_shouldBeThree() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.staged_transactions", Integer.class);
        assertEquals(EXPECTATION.getExpectedStagedCount(), count);
    }

    @Test
    void postedCount_shouldBeThree() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.transactions", Integer.class);
        assertEquals(EXPECTATION.getExpectedPostedCount(), count);
    }

    @Test
    void rejectedCount_shouldBeZero() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.staged_transactions WHERE status = 'rejected'",
            Integer.class);
        assertEquals(EXPECTATION.getExpectedRejectedCount(), count);
    }

    @Test
    void errorCount_shouldBeZero() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_job_errors", Integer.class);
        assertEquals(EXPECTATION.getExpectedErrorCount(), count);
    }

    @Test
    void totalPostedCents_shouldBe31500() {
        Long total = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM bank.transactions",
            Long.class);
        assertEquals(EXPECTATION.getExpectedTotalPostedCents(), total);
    }

    @Test
    void reconciliation_countsAndTotalsShouldMatch() {
        assertEquals(EXPECTATION.getExpectedReconciliation().getRowCount(), run.batchIds().size());

        Boolean countsMatch = jdbcTemplate.queryForObject(
            "SELECT counts_match FROM bank.batch_reconciliations WHERE batch_id = ?",
            Boolean.class,
            run.batchIds().getFirst());
        Boolean totalsMatch = jdbcTemplate.queryForObject(
            "SELECT totals_match FROM bank.batch_reconciliations WHERE batch_id = ?",
            Boolean.class,
            run.batchIds().getFirst());

        assertEquals(EXPECTATION.getExpectedReconciliation().getCountsMatch(), countsMatch);
        assertEquals(EXPECTATION.getExpectedReconciliation().getTotalsMatch(), totalsMatch);
    }

    @Test
    void reconciliation_exactlyOneRowShouldExist() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations WHERE batch_id = ?",
            Integer.class,
            run.batchIds().getFirst());
        assertEquals(EXPECTATION.getExpectedReconciliation().getRowCount(), count);
    }
}

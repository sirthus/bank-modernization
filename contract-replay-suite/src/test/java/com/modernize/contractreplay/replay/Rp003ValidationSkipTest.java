package com.modernize.contractreplay.replay;

import com.modernize.contractreplay.AbstractContractReplayTest;
import com.modernize.contractreplay.support.ReplayExpectation;
import com.modernize.contractreplay.support.ReplayExpectationLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RP-003 — Validation skip: bad row is rejected, pipeline still completes.
 *
 * The fixture contains three records, one of which violates Rule 1 (amount must
 * be positive — amount_cents=0 is not allowed). The validate step is configured
 * with a skip policy that tolerates ValidationException, so the pipeline reaches
 * COMPLETED rather than FAILED. The bad row lands in bank.batch_job_errors with
 * status='rejected'; only the two valid rows are posted and counted in
 * reconciliation.
 *
 * This test proves the skip-policy guarantee: partial input does not abort the
 * batch, and the reconciliation correctly excludes rejected rows from its totals.
 *
 * Fixture: fixtures/rp-003-validation-skip.csv
 *   account_id  merchant_id  direction  amount_cents  txn_date
 *   2001        3001         D          5000          2025-03-10  ← valid
 *   2002        3002         D          0             2025-03-10  ← INVALID (amount_cents=0)
 *   2003        (null)       C          1500          2025-03-10  ← valid
 *
 * Expected: staged=3, posted=2, rejected=1, errors=1, total_posted=6500 cents.
 * Reconciliation staged_count=2 (WHERE status='posted' excludes rejected row).
 */
class Rp003ValidationSkipTest extends AbstractContractReplayTest {

    private static final ReplayExpectation EXPECTATION =
        ReplayExpectationLoader.load("fixtures/rp-003-expected-outcome.json");

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
    void rejectedCount_shouldBeOne() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.staged_transactions WHERE status = 'rejected'",
            Integer.class);
        assertEquals(EXPECTATION.getExpectedRejectedCount(), count);
    }

    @Test
    void postedCount_shouldBeTwo() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.transactions", Integer.class);
        assertEquals(EXPECTATION.getExpectedPostedCount(), count);
    }

    @Test
    void errorLog_shouldHaveOneEntry() {
        int count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_job_errors", Integer.class);
        assertEquals(EXPECTATION.getExpectedErrorCount(), count);
    }

    @Test
    void totalPostedCents_shouldBe6500() {
        Long total = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM bank.transactions",
            Long.class);
        assertEquals(EXPECTATION.getExpectedTotalPostedCents(), total);
    }

    @Test
    void reconciliation_exactlyOneRowShouldExist() {
        assertEquals(EXPECTATION.getExpectedReconciliation().getRowCount(), run.batchIds().size());

        int rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations WHERE batch_id = ?",
            Integer.class,
            run.batchIds().getFirst());
        assertEquals(EXPECTATION.getExpectedReconciliation().getRowCount(), rowCount);
    }

    @Test
    void reconciliation_stagedCountExcludesRejectedRow() {
        // ReconcileJobConfig counts WHERE status='posted' on staged_transactions,
        // so staged_count in the reconciliation row should be 2, not 3.
        Integer stagedCount = jdbcTemplate.queryForObject(
            "SELECT staged_count FROM bank.batch_reconciliations WHERE batch_id = ?",
            Integer.class,
            run.batchIds().getFirst());
        assertEquals(EXPECTATION.getExpectedReconciliation().getStagedCount(), stagedCount,
            "Reconciliation staged_count should reflect posted-only count, excluding rejected row");
    }
}

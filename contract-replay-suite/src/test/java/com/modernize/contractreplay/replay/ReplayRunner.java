package com.modernize.contractreplay.replay;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared utility for replay test setup and pipeline execution.
 *
 * Centralises two operations that every replay test needs:
 *
 *   cleanState() — wipes all bank.* tables and Spring Batch metadata, then
 *   re-seeds the reference data (customers, accounts, merchants). Called in
 *   @BeforeEach. Testcontainers runs schema.sql once at container startup
 *   (creates tables only); seed data must be re-inserted before each test.
 *
     *   runFullPipeline() — runs all four jobs in sequence with a shared run.id,
     *   mirroring the pattern in BatchPipelineService. Returns the run.id plus the
     *   exact batch_ids loaded in that run so tests can scope assertions precisely.
 *
 * This class exists so the pattern is not duplicated across Rp001, Rp002,
 * and Rp003 test classes.
 */
public class ReplayRunner {

    private static final AtomicLong RUN_IDS = new AtomicLong(System.currentTimeMillis());

    /**
     * Clears all bank.* records and Spring Batch metadata, then re-seeds
     * the reference data needed by the validation rules.
     *
     * Deletion order is FK-safe: children before parents.
     * Seed data matches sql/012b_seed_small_data.sql exactly.
     */
    public static void cleanState(JobRepositoryTestUtils jobRepositoryTestUtils,
                                  JdbcTemplate jdbcTemplate) {
        jobRepositoryTestUtils.removeJobExecutions();

        jdbcTemplate.update("DELETE FROM bank.batch_job_errors");
        jdbcTemplate.update("DELETE FROM bank.batch_reconciliations");
        jdbcTemplate.update("DELETE FROM bank.transactions");
        jdbcTemplate.update("DELETE FROM bank.staged_transactions");
        jdbcTemplate.update("DELETE FROM bank.transaction_batches");
        jdbcTemplate.update("DELETE FROM bank.batch_jobs");
        jdbcTemplate.update("DELETE FROM bank.accounts");
        jdbcTemplate.update("DELETE FROM bank.merchants");
        jdbcTemplate.update("DELETE FROM bank.customers");

        // Re-seed reference data. Accounts 2001-2004 are used by the validation
        // rules (Rules 3 and 4: account exists, account is active).
        jdbcTemplate.update(
            "INSERT INTO bank.customers (customer_id, full_name, email, phone) VALUES " +
            "(1001, 'Alice Carter', 'alice.carter@example.com', '555-0101'), " +
            "(1002, 'Brian Nguyen', 'brian.nguyen@example.com', '555-0102')");

        jdbcTemplate.update(
            "INSERT INTO bank.accounts (account_id, customer_id, account_type, status, opened_at, credit_limit_cents) VALUES " +
            "(2001, 1001, 'checking', 'active',  CURRENT_DATE, 0), " +
            "(2002, 1001, 'credit',   'active',  CURRENT_DATE, 500000), " +
            "(2003, 1002, 'savings',  'active',  CURRENT_DATE, 0), " +
            "(2004, 1002, 'checking', 'frozen',  CURRENT_DATE, 0)");

        jdbcTemplate.update(
            "INSERT INTO bank.merchants (merchant_id, name, category) VALUES " +
            "(3001, 'H-E-B', 'groceries'), " +
            "(3002, 'Shell', 'fuel')");
    }

    /**
     * Runs all four pipeline jobs in sequence with a shared run.id.
     *
     * Mirrors the job-sequence logic in BatchPipelineService.run() but uses
     * JobLauncherTestUtils so tests can assert on BatchStatus directly.
     * Returns the run.id so callers can scope reconciliation queries to this run.
     */
    public static ReplayRun runFullPipeline(JobLauncherTestUtils launcher,
                                            Job loadJob,
                                            Job validateJob,
                                            Job postJob,
                                            Job reconcileJob,
                                            JdbcTemplate jdbcTemplate,
                                            String fileName) throws Exception {
        long runId = nextRunId(jdbcTemplate);

        launcher.setJob(loadJob);
        JobExecution load = launcher.launchJob(new JobParametersBuilder()
                .addString("fileName", fileName)
                .addLong("run.id", runId)
                .toJobParameters());
        assertCompleted(load, "loadTransactionsJob");

        launcher.setJob(validateJob);
        JobExecution validate = launcher.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertCompleted(validate, "validateTransactionsJob");

        launcher.setJob(postJob);
        JobExecution post = launcher.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertCompleted(post, "postTransactionsJob");

        launcher.setJob(reconcileJob);
        JobExecution reconcile = launcher.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertCompleted(reconcile, "reconcileJob");

        return new ReplayRun(runId, captureBatchIdsForRun(jdbcTemplate, runId));
    }

    /**
     * Runs the load, validate, post, and reconcile jobs but expects reconcile
     * to complete even when a deliberate mismatch has been injected.
     * Returns the run.id for the caller to use in the failure-check query.
     *
     * Used by RP-003 to set up a prior run with a known mismatch.
     */
    public static ReplayRun runFullPipelineExpectingMismatch(JobLauncherTestUtils launcher,
                                                             Job loadJob,
                                                             Job validateJob,
                                                             Job postJob,
                                                             Job reconcileJob,
                                                             String fileName,
                                                             JdbcTemplate jdbcTemplate) throws Exception {
        long runId = nextRunId(jdbcTemplate);

        launcher.setJob(loadJob);
        JobExecution load = launcher.launchJob(new JobParametersBuilder()
                .addString("fileName", fileName)
                .addLong("run.id", runId)
                .toJobParameters());
        assertCompleted(load, "loadTransactionsJob");

        launcher.setJob(validateJob);
        JobExecution validate = launcher.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertCompleted(validate, "validateTransactionsJob");

        launcher.setJob(postJob);
        JobExecution post = launcher.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertCompleted(post, "postTransactionsJob");

        // Sabotage: delete one transaction to cause a reconciliation mismatch.
        // The reconcile job always writes its row regardless — it's the
        // BatchPipelineService failure-check query that surfaces the mismatch.
        jdbcTemplate.update(
            "DELETE FROM bank.transactions WHERE txn_id = " +
            "(SELECT min(txn_id) FROM bank.transactions)");

        launcher.setJob(reconcileJob);
        JobExecution reconcile = launcher.launchJob(new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters());
        assertCompleted(reconcile, "reconcileJob");

        return new ReplayRun(runId, captureBatchIdsForRun(jdbcTemplate, runId));
    }

    private static void assertCompleted(JobExecution execution, String jobName) {
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            throw new AssertionError(
                jobName + " expected COMPLETED but was " + execution.getStatus() +
                ". Failures: " + execution.getAllFailureExceptions());
        }
    }

    private static long nextRunId(JdbcTemplate jdbcTemplate) {
        Long databaseNowMs = jdbcTemplate.queryForObject(
            "SELECT FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000)",
            Long.class);
        long current = databaseNowMs != null ? databaseNowMs : System.currentTimeMillis();
        return RUN_IDS.updateAndGet(previous -> Math.max(current, previous + 1));
    }

    private static List<Integer> captureBatchIdsForRun(JdbcTemplate jdbcTemplate, long runId) {
        return jdbcTemplate.queryForList(
            "SELECT tb.id " +
            "FROM bank.transaction_batches tb " +
            "JOIN bank.batch_jobs bj ON bj.id = tb.batch_job_id " +
            "WHERE bj.job_name = 'load_transactions' " +
            "  AND bj.started_at >= ? " +
            "ORDER BY tb.id",
            Integer.class,
            new Timestamp(runId));
    }
}

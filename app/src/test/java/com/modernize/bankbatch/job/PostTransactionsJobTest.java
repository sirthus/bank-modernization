package com.modernize.bankbatch.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for postTransactionsJob.
 *
 * Each test inserts staged_transactions rows with status 'validated' directly
 * via jdbcTemplate. The post job reads those rows, inserts into bank.transactions,
 * and updates staged status to 'posted'.
 *
 * Constraints to observe:
 *   bank.transactions.account_id  — FK to bank.accounts: use 2001, 2002, 2003
 *   bank.transactions.merchant_id — FK to bank.merchants: use null, 3001, or 3002
 *   bank.transactions.direction   — CHECK IN ('D','C')
 *   bank.transactions.amount_cents — CHECK > 0
 *
 * All of these are satisfied by the records that already passed validation,
 * so the test data mirrors what the validate job would have produced.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("batchtest")
class PostTransactionsJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("postTransactionsJob")
    private Job postTransactionsJob;

    @BeforeEach
    void cleanUp() {
        jobLauncherTestUtils.setJob(postTransactionsJob);
        jobRepositoryTestUtils.removeJobExecutions();

        // Delete in FK-safe order: children before parents.
        jdbcTemplate.update("DELETE FROM bank.batch_job_errors");
        jdbcTemplate.update("DELETE FROM bank.transactions");
        jdbcTemplate.update("DELETE FROM bank.staged_transactions");
        jdbcTemplate.update("DELETE FROM bank.transaction_batches");
        jdbcTemplate.update("DELETE FROM bank.batch_jobs");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int insertBatch() {
        int jobId = jdbcTemplate.queryForObject(
                "INSERT INTO bank.batch_jobs (job_name, status) " +
                "VALUES ('test_validate', 'completed') RETURNING id",
                Integer.class);
        return jdbcTemplate.queryForObject(
                "INSERT INTO bank.transaction_batches (batch_job_id, file_name, status) " +
                "VALUES (?, 'test.csv', 'received') RETURNING id",
                Integer.class, jobId);
    }

    /**
     * Inserts a staged_transaction with status 'validated' — exactly the state
     * the validate job leaves behind for valid records. merchant_id is null
     * because the post job doesn't require it and null is allowed by the schema.
     */
    private void stageValidated(int batchId, int accountId, String direction, int amountCents) {
        jdbcTemplate.update(
                "INSERT INTO bank.staged_transactions " +
                "(batch_id, account_id, direction, amount_cents, txn_date, status) " +
                "VALUES (?, ?, ?, ?, '2025-03-10', 'validated')",
                batchId, accountId, direction, amountCents);
    }

    private JobExecution runPostJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        return jobLauncherTestUtils.launchJob(params);
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Test
    void postJob_validatedRecords_completesAndInsertsTransactions() throws Exception {
        int batchId = insertBatch();
        stageValidated(batchId, 2001, "D", 5000);
        stageValidated(batchId, 2002, "C", 1500);
        stageValidated(batchId, 2003, "C", 25000);

        JobExecution execution = runPostJob();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // One row inserted into bank.transactions per staged record.
        int txnCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.transactions", Integer.class);
        assertThat(txnCount).isEqualTo(3);

        // Staged records must be marked posted, not left as validated.
        int postedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'posted'",
                Integer.class);
        assertThat(postedCount).isEqualTo(3);
    }

    // =========================================================================
    // Only validated records are posted — rejected ones are left alone
    // =========================================================================

    @Test
    void postJob_mixOfValidatedAndRejected_onlyValidatedArePosted() throws Exception {
        int batchId = insertBatch();
        stageValidated(batchId, 2001, "D", 5000);  // validated — should be posted
        stageValidated(batchId, 2003, "C", 1500);  // validated — should be posted

        // Insert a rejected record directly — the post job must ignore it.
        jdbcTemplate.update(
                "INSERT INTO bank.staged_transactions " +
                "(batch_id, account_id, direction, amount_cents, txn_date, status) " +
                "VALUES (?, 2002, 'D', 0, '2025-03-10', 'rejected')",
                batchId);

        runPostJob();

        // Only the 2 validated records become transactions.
        int txnCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.transactions", Integer.class);
        assertThat(txnCount).isEqualTo(2);

        // The rejected record must still be rejected — post job must not touch it.
        int rejectedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'rejected'",
                Integer.class);
        assertThat(rejectedCount).isEqualTo(1);
    }

    // =========================================================================
    // Transaction data correctness
    // =========================================================================

    @Test
    void postJob_transactionDataMatchesStagedRecord() throws Exception {
        int batchId = insertBatch();
        stageValidated(batchId, 2001, "D", 7500);

        runPostJob();

        // Verify the actual values written to bank.transactions.
        Map<String, Object> txn = jdbcTemplate.queryForMap(
                "SELECT account_id, direction, amount_cents, status, description " +
                "FROM bank.transactions");

        assertThat(txn.get("account_id")).isEqualTo(2001);
        assertThat(txn.get("direction").toString().trim()).isEqualTo("D");
        assertThat(txn.get("amount_cents")).isEqualTo(7500);
        assertThat(txn.get("status")).isEqualTo("posted");
        assertThat(txn.get("description")).isEqualTo("Batch posted");
    }

    // =========================================================================
    // No validated records — job completes with nothing to do
    // =========================================================================

    @Test
    void postJob_noValidatedRecords_completesWithNoTransactions() throws Exception {
        // No staged records at all — partitioner finds no batch_ids.
        JobExecution execution = runPostJob();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        int txnCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.transactions", Integer.class);
        assertThat(txnCount).isEqualTo(0);
    }
}

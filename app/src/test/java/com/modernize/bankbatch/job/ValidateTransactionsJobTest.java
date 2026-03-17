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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for validateTransactionsJob.
 *
 * Each test inserts staged records directly via jdbcTemplate — no dependency
 * on the load job. This keeps each test self-contained and explicit about
 * exactly what data the validate job will see.
 *
 * Seed data in modernize_buildtest (012b_seed_small_data.sql):
 *   account 2001 — active    (Rules 3 and 4 pass)
 *   account 2002 — active    (Rules 3 and 4 pass)
 *   account 2003 — active    (Rules 3 and 4 pass)
 *   account 2004 — frozen    (Rule 4 fails: account is not active)
 *   account 9999 — not in db (Rule 3 fails: account not found, LEFT JOIN returns null)
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("batchtest")
class ValidateTransactionsJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("validateTransactionsJob")
    private Job validateTransactionsJob;

    @BeforeEach
    void cleanUp() {
        jobLauncherTestUtils.setJob(validateTransactionsJob);
        jobRepositoryTestUtils.removeJobExecutions();

        // Delete in FK-safe order: children before parents.
        jdbcTemplate.update("DELETE FROM bank.batch_job_errors");
        jdbcTemplate.update("DELETE FROM bank.batch_reconciliations");
        jdbcTemplate.update("DELETE FROM bank.transactions");
        jdbcTemplate.update("DELETE FROM bank.staged_transactions");
        jdbcTemplate.update("DELETE FROM bank.transaction_batches");
        jdbcTemplate.update("DELETE FROM bank.batch_jobs");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a batch_job + transaction_batch row and returns the batch_id.
     * The validate job's partitioner queries staged_transactions.batch_id,
     * which must reference a real transaction_batches row.
     */
    private int insertBatch() {
        int jobId = jdbcTemplate.queryForObject(
                "INSERT INTO bank.batch_jobs (job_name, status) " +
                "VALUES ('test_load', 'completed') RETURNING id",
                Integer.class);
        return jdbcTemplate.queryForObject(
                "INSERT INTO bank.transaction_batches (batch_job_id, file_name, status) " +
                "VALUES (?, 'test.csv', 'received') RETURNING id",
                Integer.class, jobId);
    }

    /**
     * Inserts one staged_transaction row. merchant_id is null — the validation
     * rules do not check it, and null is allowed by the schema.
     */
    private void stage(int batchId, int accountId, String direction, int amountCents) {
        jdbcTemplate.update(
                "INSERT INTO bank.staged_transactions " +
                "(batch_id, account_id, direction, amount_cents, txn_date, status) " +
                "VALUES (?, ?, ?, ?, '2025-03-10', 'staged')",
                batchId, accountId, direction, amountCents);
    }

    /**
     * Launches the validate job. No file parameter needed — the job reads
     * whatever is in staged_transactions with status 'staged'.
     * run.id makes each launch a unique job instance so Spring Batch does
     * not treat it as a re-run of a previously completed job.
     */
    private JobExecution runValidateJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        return jobLauncherTestUtils.launchJob(params);
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Test
    void validateJob_allValidRecords_allBecomesValidated() throws Exception {
        int batchId = insertBatch();
        stage(batchId, 2001, "D", 5000);
        stage(batchId, 2002, "C", 1500);
        stage(batchId, 2003, "C", 25000);

        JobExecution execution = runValidateJob();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        int validated = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'validated'",
                Integer.class);
        assertThat(validated).isEqualTo(3);

        int rejected = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'rejected'",
                Integer.class);
        assertThat(rejected).isEqualTo(0);

        int errors = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.batch_job_errors", Integer.class);
        assertThat(errors).isEqualTo(0);
    }

    // =========================================================================
    // Rule 1: amount must be positive
    // =========================================================================

    @Test
    void validateJob_invalidAmounts_rejectedWithErrorLogged() throws Exception {
        int batchId = insertBatch();
        stage(batchId, 2001, "D", 0);    // Rule 1 fails: zero
        stage(batchId, 2002, "D", -1);   // Rule 1 fails: negative
        stage(batchId, 2003, "C", 500);  // passes all rules

        runValidateJob();

        int validated = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'validated'",
                Integer.class);
        assertThat(validated).isEqualTo(1);

        int rejected = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'rejected'",
                Integer.class);
        assertThat(rejected).isEqualTo(2);

        // One error row per rejected record, each containing the rule message.
        List<String> errors = jdbcTemplate.queryForList(
                "SELECT error_message FROM bank.batch_job_errors", String.class);
        assertThat(errors).hasSize(2);
        assertThat(errors).allMatch(msg -> msg.contains("amount must be positive"));
    }

    // =========================================================================
    // Rule 3: account must exist
    // =========================================================================

    @Test
    void validateJob_unknownAccount_rejectedWithAccountNotFoundError() throws Exception {
        int batchId = insertBatch();
        stage(batchId, 9999, "D", 500);  // account 9999 not in bank.accounts

        runValidateJob();

        int rejected = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'rejected'",
                Integer.class);
        assertThat(rejected).isEqualTo(1);

        String errorMsg = jdbcTemplate.queryForObject(
                "SELECT error_message FROM bank.batch_job_errors", String.class);
        assertThat(errorMsg).contains("account not found");
    }

    // =========================================================================
    // Rule 4: account must be active
    // =========================================================================

    @Test
    void validateJob_inactiveAccount_rejectedWithAccountNotActiveError() throws Exception {
        int batchId = insertBatch();
        stage(batchId, 2004, "D", 500);  // account 2004 is frozen in seed data

        runValidateJob();

        int rejected = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'rejected'",
                Integer.class);
        assertThat(rejected).isEqualTo(1);

        String errorMsg = jdbcTemplate.queryForObject(
                "SELECT error_message FROM bank.batch_job_errors", String.class);
        assertThat(errorMsg).contains("account is not active");
    }

    // =========================================================================
    // Mixed: valid and invalid in same batch
    // =========================================================================

    @Test
    void validateJob_mixedRecords_correctCountsValidatedAndRejected() throws Exception {
        int batchId = insertBatch();
        stage(batchId, 2001, "D", 5000);  // valid
        stage(batchId, 2002, "D", 0);     // Rule 1: zero amount
        stage(batchId, 2003, "C", 1500);  // valid
        stage(batchId, 9999, "D", 750);   // Rule 3: unknown account
        stage(batchId, 2001, "X", 200);   // Rule 2: invalid direction

        runValidateJob();

        int validated = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'validated'",
                Integer.class);
        assertThat(validated).isEqualTo(2);

        int rejected = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'rejected'",
                Integer.class);
        assertThat(rejected).isEqualTo(3);

        int errors = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.batch_job_errors", Integer.class);
        assertThat(errors).isEqualTo(3);
    }
}

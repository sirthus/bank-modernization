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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for loadTransactionsJob.
 *
 * Runs against modernize_buildtest (the sandbox database), which must be
 * running and seeded before the test executes. @BeforeEach clears staged
 * data so each test starts from a known baseline.
 *
 * @SpringBatchTest wires in JobLauncherTestUtils and JobRepositoryTestUtils.
 * @SpringBootTest loads the full application context.
 * "batchtest" profile supplies the datasource; BatchRunner is @Profile("sandbox")
 * so it does not fire here. spring.batch.job.enabled=false (application.properties
 * in src/test/resources) prevents any job from running on startup.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("batchtest")
class LoadTransactionsJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Multiple Job beans exist in the context; tell JobLauncherTestUtils
    // which one to use, otherwise it is left null and every launch fails.
    @Autowired
    @Qualifier("loadTransactionsJob")
    private Job loadTransactionsJob;

    @BeforeEach
    void cleanUp() {
        jobLauncherTestUtils.setJob(loadTransactionsJob);
        // Remove Spring Batch job execution records so the same fileName
        // parameter can be reused across tests without a "job already complete"
        // collision.
        jobRepositoryTestUtils.removeJobExecutions();

        // Clear staged data so each test asserts against a known baseline.
        // Delete in FK-safe order: children before parents.
        jdbcTemplate.update("DELETE FROM bank.batch_job_errors");
        jdbcTemplate.update("DELETE FROM bank.batch_reconciliations");
        jdbcTemplate.update("DELETE FROM bank.transactions");
        jdbcTemplate.update("DELETE FROM bank.staged_transactions");
        jdbcTemplate.update("DELETE FROM bank.transaction_batches");
        jdbcTemplate.update("DELETE FROM bank.batch_jobs");
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Test
    void loadJob_allValidFile_completesAndStagesAllRecords() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("fileName", "test_all_valid.csv")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        int staged = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions", Integer.class);
        assertThat(staged).isEqualTo(3);

        // All records enter with status 'staged' — validation has not run yet.
        int stagedStatus = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status = 'staged'",
                Integer.class);
        assertThat(stagedStatus).isEqualTo(3);
    }

    // =========================================================================
    // Record count per file
    // =========================================================================

    @Test
    void loadJob_invalidAmountsFile_stagesAllThreeRecords() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("fileName", "test_invalid_amounts.csv")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Load job accepts all rows regardless of amount validity —
        // that is the validate job's responsibility.
        int staged = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions", Integer.class);
        assertThat(staged).isEqualTo(3);
    }

    @Test
    void loadJob_singleRowFile_stagesExactlyOneRecord() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("fileName", "test_unknown_account.csv")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        int staged = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions", Integer.class);
        assertThat(staged).isEqualTo(1);
    }

    // =========================================================================
    // Status after load
    // =========================================================================

    @Test
    void loadJob_allRecordsArrivedWithStagedStatus() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("fileName", "test_mixed.csv")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        jobLauncherTestUtils.launchJob(params);

        // The load job must not pre-validate or pre-reject anything.
        // Every record lands with status 'staged'.
        int nonStaged = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.staged_transactions WHERE status != 'staged'",
                Integer.class);
        assertThat(nonStaged).isEqualTo(0);
    }
}

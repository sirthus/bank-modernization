package com.modernize.verificationlab;

import com.modernize.verificationlab.approved.ApprovedDifferenceLoader;
import com.modernize.verificationlab.baseline.BaselineLoader;
import com.modernize.verificationlab.collector.ActualOutputCollector;
import com.modernize.verificationlab.engine.VerificationEngine;
import com.modernize.verificationlab.model.ApprovedDifference;
import com.modernize.verificationlab.model.ActualOutput;
import com.modernize.verificationlab.model.BaselineOutput;
import com.modernize.verificationlab.model.DiscrepancyClassification;
import com.modernize.verificationlab.model.VerificationResult;
import com.modernize.verificationlab.report.ReportWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.modernize.bankbatch.BankBatchApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DS-002 — Monetary Precision / Accumulating Drift.
 *
 * Four records post correctly by count and business key. The legacy baseline
 * reflects truncated amounts; the modern pipeline stores correctly-rounded values.
 * Three of four records carry a per-record delta of +1 or +2 cents. No approval
 * entry exists.
 *
 * Expected result: FAIL
 *
 * This test asserts that the lab catches unapproved monetary divergence.
 * If this test passes (unexpectedly), the comparison engine has a false negative.
 */
@SpringBatchTest
@SpringBootTest(classes = BankBatchApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("verificationlab")
class Ds002MonetaryPrecisionTest {

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired private JobRepositoryTestUtils jobRepositoryTestUtils;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired @Qualifier("loadTransactionsJob")     private Job loadTransactionsJob;
    @Autowired @Qualifier("validateTransactionsJob") private Job validateTransactionsJob;
    @Autowired @Qualifier("postTransactionsJob")     private Job postTransactionsJob;
    @Autowired @Qualifier("reconcileJob")            private Job reconcileJob;

    private final VerificationEngine engine = new VerificationEngine();

    @BeforeEach
    void cleanUp() {
        jobRepositoryTestUtils.removeJobExecutions();
        jdbcTemplate.update("DELETE FROM bank.batch_job_errors");
        jdbcTemplate.update("DELETE FROM bank.batch_reconciliations");
        jdbcTemplate.update("DELETE FROM bank.transactions");
        jdbcTemplate.update("DELETE FROM bank.staged_transactions");
        jdbcTemplate.update("DELETE FROM bank.transaction_batches");
        jdbcTemplate.update("DELETE FROM bank.batch_jobs");
    }

    @Test
    void ds002_monetaryDrift_withNoApprovalEntry_producesFail() throws Exception {
        runFullPipeline("vl-ds002-monetary.csv");

        ActualOutput actual = ActualOutputCollector.collect(jdbcTemplate);
        BaselineOutput baseline = BaselineLoader.load("ds-002");
        List<ApprovedDifference> approved = ApprovedDifferenceLoader.load("ds-002");

        VerificationResult result = engine.compare(actual, baseline, approved,
            resolveCommitSha(), "verificationlab");
        ReportWriter.write(result);

        assertThat(result.getOverallStatus())
            .as("DS-002 should FAIL: monetary divergence exists with no approval entry")
            .isEqualTo(DiscrepancyClassification.FAIL);
        assertThat(result.getFailCount())
            .as("At least the totalPostedCents and per-record amounts should fail")
            .isGreaterThan(0);
        assertThat(result.getApprovedCount())
            .as("No approval entries exist for DS-002 by default")
            .isZero();
    }

    private void runFullPipeline(String fileName) throws Exception {
        long runId = System.currentTimeMillis();

        jobLauncherTestUtils.setJob(loadTransactionsJob);
        JobExecution load = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addString("fileName", fileName)
            .addLong("run.id", runId)
            .toJobParameters());
        assertThat(load.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        jobLauncherTestUtils.setJob(validateTransactionsJob);
        JobExecution validate = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addLong("run.id", runId)
            .toJobParameters());
        assertThat(validate.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        jobLauncherTestUtils.setJob(postTransactionsJob);
        JobExecution post = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addLong("run.id", runId)
            .toJobParameters());
        assertThat(post.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        jobLauncherTestUtils.setJob(reconcileJob);
        JobExecution reconcile = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
            .addLong("run.id", runId)
            .toJobParameters());
        assertThat(reconcile.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    private String resolveCommitSha() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(new java.io.File(System.getProperty("user.dir")))
                .start();
            return new String(process.getInputStream().readAllBytes()).trim();
        } catch (Exception e) {
            return "unknown";
        }
    }
}

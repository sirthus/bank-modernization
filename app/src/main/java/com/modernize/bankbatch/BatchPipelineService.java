package com.modernize.bankbatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the pipeline execution logic.
 *
 * BatchRunner (sandbox CommandLineRunner), BatchScheduler (@Scheduled),
 * and BatchController (REST) all call this service. The AtomicBoolean
 * guard prevents two callers from running the pipeline at the same time —
 * the same role a Control-M job fence plays in production.
 */
@Service
public class BatchPipelineService {

    private static final Logger log = LoggerFactory.getLogger(BatchPipelineService.class);

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final JobLauncher jobLauncher;
    private final Job loadTransactionsJob;
    private final Job validateTransactionsJob;
    private final Job postTransactionsJob;
    private final Job reconcileJob;
    private final BatchSummaryReport summaryReport;
    private final BatchPipelineProperties pipelineProperties;
    private final JdbcTemplate jdbcTemplate;

    public BatchPipelineService(JobLauncher jobLauncher,
                                @Qualifier("loadTransactionsJob") Job loadTransactionsJob,
                                @Qualifier("validateTransactionsJob") Job validateTransactionsJob,
                                @Qualifier("postTransactionsJob") Job postTransactionsJob,
                                @Qualifier("reconcileJob") Job reconcileJob,
                                BatchSummaryReport summaryReport,
                                BatchPipelineProperties pipelineProperties,
                                JdbcTemplate jdbcTemplate) {
        this.jobLauncher = jobLauncher;
        this.loadTransactionsJob = loadTransactionsJob;
        this.validateTransactionsJob = validateTransactionsJob;
        this.postTransactionsJob = postTransactionsJob;
        this.reconcileJob = reconcileJob;
        this.summaryReport = summaryReport;
        this.pipelineProperties = pipelineProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns true while a pipeline run is in progress.
     * Used by BatchController to report status.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Runs the full pipeline: load → validate → post → reconcile → report.
     *
     * Throws IllegalStateException immediately if already running.
     * All other exceptions propagate to the caller (scheduler logs them;
     * REST controller returns 500).
     */
    public void run() throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Pipeline is already running");
        }

        try {
            Date runTimestamp = new Date();
            MDC.put("pipeline.runId", String.valueOf(runTimestamp.getTime()));

            log.info("Pipeline starting");

            MDC.put("job.name", "loadTransactionsJob");
            for (String fileName : pipelineProperties.getFiles()) {
                jobLauncher.run(loadTransactionsJob,
                    new JobParametersBuilder()
                        .addString("fileName", fileName)
                        .addLong("run.id", runTimestamp.getTime())
                        .toJobParameters());
            }

            MDC.put("job.name", "validateTransactionsJob");
            jobLauncher.run(validateTransactionsJob,
                new JobParametersBuilder()
                    .addLong("run.id", runTimestamp.getTime())
                    .toJobParameters());

            MDC.put("job.name", "postTransactionsJob");
            jobLauncher.run(postTransactionsJob,
                new JobParametersBuilder()
                    .addLong("run.id", runTimestamp.getTime())
                    .toJobParameters());

            MDC.put("job.name", "reconcileJob");
            jobLauncher.run(reconcileJob,
                new JobParametersBuilder()
                    .addLong("run.id", runTimestamp.getTime())
                    .toJobParameters());

            // Fail the pipeline if any batch did not balance. The reconcile job
            // always completes (so audit rows are committed), but we surface the
            // discrepancy here before generating the summary report.
            Integer reconFailures = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bank.batch_reconciliations br " +
                "JOIN bank.batch_jobs bj ON bj.id = br.batch_job_id " +
                "WHERE bj.started_at >= ? " +
                "  AND (NOT br.counts_match OR NOT br.totals_match)",
                Integer.class, new Timestamp(runTimestamp.getTime()));

            if (reconFailures != null && reconFailures > 0) {
                throw new RuntimeException(
                    "Pipeline halted: reconciliation failed for " + reconFailures +
                    " batch(es) — check bank.batch_reconciliations for details");
            }

            MDC.remove("job.name");
            summaryReport.print();

            log.info("Pipeline complete");

        } finally {
            MDC.clear();
            running.set(false);
        }
    }
}

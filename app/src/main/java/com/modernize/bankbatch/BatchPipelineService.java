package com.modernize.bankbatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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

    public BatchPipelineService(JobLauncher jobLauncher,
                                @Qualifier("loadTransactionsJob") Job loadTransactionsJob,
                                @Qualifier("validateTransactionsJob") Job validateTransactionsJob,
                                @Qualifier("postTransactionsJob") Job postTransactionsJob,
                                @Qualifier("reconcileJob") Job reconcileJob,
                                BatchSummaryReport summaryReport,
                                BatchPipelineProperties pipelineProperties) {
        this.jobLauncher = jobLauncher;
        this.loadTransactionsJob = loadTransactionsJob;
        this.validateTransactionsJob = validateTransactionsJob;
        this.postTransactionsJob = postTransactionsJob;
        this.reconcileJob = reconcileJob;
        this.summaryReport = summaryReport;
        this.pipelineProperties = pipelineProperties;
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

            log.info("Pipeline starting");

            for (String fileName : pipelineProperties.getFiles()) {
                jobLauncher.run(loadTransactionsJob,
                    new JobParametersBuilder()
                        .addString("fileName", fileName)
                        .addDate("run.id", runTimestamp)
                        .toJobParameters());
            }

            jobLauncher.run(validateTransactionsJob,
                new JobParametersBuilder()
                    .addDate("run.id", runTimestamp)
                    .toJobParameters());

            jobLauncher.run(postTransactionsJob,
                new JobParametersBuilder()
                    .addDate("run.id", runTimestamp)
                    .toJobParameters());

            jobLauncher.run(reconcileJob,
                new JobParametersBuilder()
                    .addDate("run.id", runTimestamp)
                    .toJobParameters());

            summaryReport.print();

            log.info("Pipeline complete");

        } finally {
            running.set(false);
        }
    }
}

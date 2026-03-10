package com.modernize.bankbatch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class BatchRunner implements CommandLineRunner {

    private final JobLauncher jobLauncher;
    private final Job loadTransactionsJob;
    private final Job validateTransactionsJob;
    private final Job postTransactionsJob;
    private final Job reconcileJob;
    private final BatchSummaryReport summaryReport;
    private final BatchPipelineProperties pipelineProperties;

    public BatchRunner(JobLauncher jobLauncher,
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

    @Override
    public void run(String... args) throws Exception {
        Date runTimestamp = new Date();

        // Load all inbound files from config
        for (String fileName : pipelineProperties.getFiles()) {
            jobLauncher.run(loadTransactionsJob,
                new JobParametersBuilder()
                    .addString("fileName", fileName)
                    .addDate("run.id", runTimestamp)
                    .toJobParameters());
        }

        // Validate all staged records
        jobLauncher.run(validateTransactionsJob,
            new JobParametersBuilder()
                .addDate("run.id", runTimestamp)
                .toJobParameters());

        // Post validated records
        jobLauncher.run(postTransactionsJob,
            new JobParametersBuilder()
                .addDate("run.id", runTimestamp)
                .toJobParameters());

        // Reconcile
        jobLauncher.run(reconcileJob,
            new JobParametersBuilder()
                .addDate("run.id", runTimestamp)
                .toJobParameters());

        // Print summary
        summaryReport.print();
    }
}

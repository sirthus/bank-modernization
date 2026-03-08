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

    public BatchRunner(JobLauncher jobLauncher,
                       @Qualifier("loadTransactionsJob") Job loadTransactionsJob,
                       @Qualifier("validateTransactionsJob") Job validateTransactionsJob,
                       @Qualifier("postTransactionsJob") Job postTransactionsJob,
                       @Qualifier("reconcileJob") Job reconcileJob,
                       BatchSummaryReport summaryReport) {
        this.jobLauncher = jobLauncher;
        this.loadTransactionsJob = loadTransactionsJob;
        this.validateTransactionsJob = validateTransactionsJob;
        this.postTransactionsJob = postTransactionsJob;
        this.reconcileJob = reconcileJob;
        this.summaryReport = summaryReport;
    }

    @Override
    public void run(String... args) throws Exception {
        Date runTimestamp = new Date();

        String[] files = {
            "ach_20250310.csv",
            "ach_20250317.csv",
            "ach_20250324.csv"
        };

        // Load all inbound files
        for (String fileName : files) {
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

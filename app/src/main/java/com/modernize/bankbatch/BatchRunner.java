package com.modernize.bankbatch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class BatchRunner implements CommandLineRunner {

    private final JobLauncher jobLauncher;
    private final Job loadTransactionsJob;
    private final Job validateTransactionsJob;
    private final Job postTransactionsJob;
    private final Job reconcileJob;

    public BatchRunner(JobLauncher jobLauncher,
                       @Qualifier("loadTransactionsJob") Job loadTransactionsJob,
                       @Qualifier("validateTransactionsJob") Job validateTransactionsJob,
                       @Qualifier("postTransactionsJob") Job postTransactionsJob,
                       @Qualifier("reconcileJob") Job reconcileJob) {
        this.jobLauncher = jobLauncher;
        this.loadTransactionsJob = loadTransactionsJob;
        this.validateTransactionsJob = validateTransactionsJob;
        this.postTransactionsJob = postTransactionsJob;
        this.reconcileJob = reconcileJob;
    }

    @Override
    public void run(String... args) throws Exception {

        // Load the good file
        jobLauncher.run(loadTransactionsJob,
            new JobParametersBuilder()
                .addString("fileName", "ach_20250307.csv")
                .toJobParameters());

        // Load the bad file
        jobLauncher.run(loadTransactionsJob,
            new JobParametersBuilder()
                .addString("fileName", "ach_20250308_bad.csv")
                .toJobParameters());

        // Validate all staged records
        jobLauncher.run(validateTransactionsJob, new JobParameters());

        // Post validated records
        jobLauncher.run(postTransactionsJob, new JobParameters());

        // Reconcile
        jobLauncher.run(reconcileJob, new JobParameters());
    }
}

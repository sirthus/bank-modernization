package com.modernize.bankbatch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
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
        jobLauncher.run(loadTransactionsJob, new JobParameters());
        jobLauncher.run(validateTransactionsJob, new JobParameters());
        jobLauncher.run(postTransactionsJob, new JobParameters());
        jobLauncher.run(reconcileJob, new JobParameters());
    }
}

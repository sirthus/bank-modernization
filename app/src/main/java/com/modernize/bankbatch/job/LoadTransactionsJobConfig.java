package com.modernize.bankbatch.job;

import com.modernize.bankbatch.listener.LoadJobCompletionListener;
import com.modernize.bankbatch.listener.ProgressListener;
import com.modernize.bankbatch.model.StagedTransaction;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class LoadTransactionsJobConfig {

    private final AtomicInteger batchId = new AtomicInteger();

    @Bean
    @StepScope
    public Tasklet setupBatchTasklet(JdbcTemplate jdbcTemplate,
                                     @Value("#{jobParameters['fileName']}") String fileName) {
        return (contribution, chunkContext) -> {

            Integer jobId = jdbcTemplate.queryForObject(
                "INSERT INTO bank.batch_jobs (job_name, status) " +
                "VALUES ('load_transactions', 'running') RETURNING id",
                Integer.class);

            Integer id = jdbcTemplate.queryForObject(
                "INSERT INTO bank.transaction_batches (batch_job_id, file_name, status) " +
                "VALUES (?, ?, 'received') RETURNING id",
                Integer.class, jobId, fileName);

            batchId.set(id);

            // Store IDs in execution context so the listener can access them
            chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext()
                .putInt("batchJobId", jobId);
            chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext()
                .putInt("batchId", id);

            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step setupStep(JobRepository jobRepository,
                          PlatformTransactionManager transactionManager,
                          Tasklet setupBatchTasklet) {
        return new StepBuilder("setupStep", jobRepository)
                .tasklet(setupBatchTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<StagedTransaction> csvReader(
            @Value("#{jobParameters['fileName']}") String fileName) {
        return new FlatFileItemReaderBuilder<StagedTransaction>()
                .name("csvReader")
                .resource(new ClassPathResource(fileName))
                .delimited()
                .names("accountId", "merchantId", "direction", "amountCents", "txnDate")
                .targetType(StagedTransaction.class)
                .linesToSkip(1)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<StagedTransaction> stagingWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<StagedTransaction>()
                .sql("INSERT INTO bank.staged_transactions " +
                     "(batch_id, account_id, merchant_id, direction, amount_cents, txn_date, status) " +
                     "VALUES (:batchId, :accountId, :merchantId, :direction, :amountCents, " +
                     "CAST(:txnDate AS DATE), 'staged')")
                .dataSource(dataSource)
                .beanMapped()
                .build();
    }

    @Bean
    public Step loadStep(JobRepository jobRepository,
                         PlatformTransactionManager transactionManager,
                         FlatFileItemReader<StagedTransaction> csvReader,
                         JdbcBatchItemWriter<StagedTransaction> stagingWriter,
                         ProgressListener progressListener) {
        return new StepBuilder("loadStep", jobRepository)
                .<StagedTransaction, StagedTransaction>chunk(500, transactionManager)
                .reader(csvReader)
                .processor(item -> {
                    item.setBatchId(batchId.get());
                    return item;
                })
                .writer(stagingWriter)
                .listener(progressListener)
                .build();
    }

    @Bean
    public Job loadTransactionsJob(JobRepository jobRepository,
                                   Step setupStep,
                                   Step loadStep,
                                   LoadJobCompletionListener listener) {
        return new JobBuilder("loadTransactionsJob", jobRepository)
                .listener(listener)
                .start(setupStep)
                .next(loadStep)
                .build();
    }
}

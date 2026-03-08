package com.modernize.bankbatch.job;

import com.modernize.bankbatch.listener.LoadJobCompletionListener;
import com.modernize.bankbatch.model.StagedTransaction;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
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
    public Tasklet setupBatchTasklet(JdbcTemplate jdbcTemplate) {
        return (contribution, chunkContext) -> {

            // Create the batch job row
            Integer jobId = jdbcTemplate.queryForObject(
                "INSERT INTO bank.batch_jobs (job_name, status) " +
                "VALUES ('load_transactions', 'running') RETURNING id",
                Integer.class);

            // Create the transaction batch row
            Integer id = jdbcTemplate.queryForObject(
                "INSERT INTO bank.transaction_batches (batch_job_id, file_name, status) " +
                "VALUES (?, 'ach_20250307.csv', 'received') RETURNING id",
                Integer.class, jobId);

            batchId.set(id);

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
    public FlatFileItemReader<StagedTransaction> csvReader() {
        return new FlatFileItemReaderBuilder<StagedTransaction>()
                .name("csvReader")
                .resource(new ClassPathResource("ach_20250307.csv"))
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
                         JdbcBatchItemWriter<StagedTransaction> stagingWriter) {
        return new StepBuilder("loadStep", jobRepository)
                .<StagedTransaction, StagedTransaction>chunk(10, transactionManager)
                .reader(csvReader)
                .processor(item -> {
                    item.setBatchId(batchId.get());
                    return item;
                })
                .writer(stagingWriter)
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

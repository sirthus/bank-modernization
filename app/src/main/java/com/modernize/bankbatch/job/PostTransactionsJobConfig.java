package com.modernize.bankbatch.job;

import com.modernize.bankbatch.listener.PostJobCompletionListener;
import com.modernize.bankbatch.listener.ProgressListener;
import com.modernize.bankbatch.model.StagedTransaction;
import com.modernize.bankbatch.partitioner.BatchIdPartitioner;
import com.modernize.bankbatch.writer.PostingWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class PostTransactionsJobConfig {

    @Bean
    public Tasklet postSetupTasklet(JdbcTemplate jdbcTemplate) {
        return (contribution, chunkContext) -> {

            jdbcTemplate.queryForObject(
                "INSERT INTO bank.batch_jobs (job_name, status) " +
                "VALUES ('post_transactions', 'running') RETURNING id",
                Integer.class);

            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step postSetupStep(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              Tasklet postSetupTasklet) {
        return new StepBuilder("postSetupStep", jobRepository)
                .tasklet(postSetupTasklet, transactionManager)
                .build();
    }

    @Bean
    public Partitioner postPartitioner(JdbcTemplate jdbcTemplate) {
        return new BatchIdPartitioner(jdbcTemplate, "validated");
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<StagedTransaction> partitionedValidatedReader(
            DataSource dataSource,
            @Value("#{stepExecutionContext['batchId']}") Integer batchId) {
        return new JdbcCursorItemReaderBuilder<StagedTransaction>()
                .name("partitionedValidatedReader")
                .dataSource(dataSource)
                .sql("SELECT id, batch_id, account_id, merchant_id, direction, amount_cents, txn_date " +
                     "FROM bank.staged_transactions " +
                     "WHERE status = 'validated' AND batch_id = ?")
                .queryArguments(batchId)
                .rowMapper((rs, rowNum) -> {
                    StagedTransaction item = new StagedTransaction();
                    item.setId(rs.getInt("id"));
                    item.setBatchId(rs.getInt("batch_id"));
                    item.setAccountId(rs.getInt("account_id"));
                    int merchantId = rs.getInt("merchant_id");
                    item.setMerchantId(rs.wasNull() ? null : merchantId);
                    item.setDirection(rs.getString("direction").trim());
                    item.setAmountCents(rs.getInt("amount_cents"));
                    item.setTxnDate(rs.getString("txn_date"));
                    return item;
                })
                .build();
    }

    @Bean
    public PostingWriter postingWriter(JdbcTemplate jdbcTemplate) {
        return new PostingWriter(jdbcTemplate);
    }

    @Bean
    public Step postWorkerStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               JdbcCursorItemReader<StagedTransaction> partitionedValidatedReader,
                               PostingWriter postingWriter,
                               ProgressListener progressListener) {
        return new StepBuilder("postWorkerStep", jobRepository)
                .<StagedTransaction, StagedTransaction>chunk(500, transactionManager)
                .reader(partitionedValidatedReader)
                .writer(postingWriter)
                .listener(progressListener)
                .build();
    }

    @Bean
    public Step postStep(JobRepository jobRepository,
                         Step postWorkerStep,
                         Partitioner postPartitioner) {

        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("post-");
        taskExecutor.setConcurrencyLimit(4);

        return new StepBuilder("postStep", jobRepository)
                .partitioner("postWorkerStep", postPartitioner)
                .step(postWorkerStep)
                .taskExecutor(taskExecutor)
                .build();
    }

    @Bean
    public Job postTransactionsJob(JobRepository jobRepository,
                                   Step postSetupStep,
                                   Step postStep,
                                   PostJobCompletionListener listener) {
        return new JobBuilder("postTransactionsJob", jobRepository)
                .listener(listener)
                .start(postSetupStep)
                .next(postStep)
                .build();
    }
}

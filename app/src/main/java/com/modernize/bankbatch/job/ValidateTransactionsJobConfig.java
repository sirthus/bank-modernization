package com.modernize.bankbatch.job;

import com.modernize.bankbatch.exception.ValidationException;
import com.modernize.bankbatch.listener.ProgressListener;
import com.modernize.bankbatch.listener.ValidateJobCompletionListener;
import com.modernize.bankbatch.listener.ValidationSkipListener;
import com.modernize.bankbatch.model.StagedTransaction;
import com.modernize.bankbatch.partitioner.BatchIdPartitioner;
import com.modernize.bankbatch.processor.ValidationProcessor;
import com.modernize.bankbatch.writer.ValidationWriter;
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
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
/**
 * Configures the validation job that partitions staged work by batch_id.
 *
 * Validation is intentionally fault-tolerant: bad records are rejected and
 * logged, while the rest of the batch continues toward posting.
 */
public class ValidateTransactionsJobConfig {

    private final AtomicInteger validateJobId = new AtomicInteger();

    @Bean
    public Tasklet validateSetupTasklet(JdbcTemplate jdbcTemplate,
                                        ValidationSkipListener skipListener) {
        return (contribution, chunkContext) -> {

            // Persist the job row before worker partitions start so every skip
            // can log against the same validate_transactions execution record.
            Integer jobId = jdbcTemplate.queryForObject(
                "INSERT INTO bank.batch_jobs (job_name, status) " +
                "VALUES ('validate_transactions', 'running') RETURNING id",
                Integer.class);

            validateJobId.set(jobId);
            // The skip listener runs inside worker chunks, so share the current
            // validation job id with it before any partition starts processing.
            skipListener.setValidateJobId(validateJobId);

            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step validateSetupStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  Tasklet validateSetupTasklet) {
        return new StepBuilder("validateSetupStep", jobRepository)
                .tasklet(validateSetupTasklet, transactionManager)
                .build();
    }

    @Bean
    public Partitioner validatePartitioner(JdbcTemplate jdbcTemplate) {
        return new BatchIdPartitioner(jdbcTemplate, "staged");
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<StagedTransaction> partitionedStagedReader(
            DataSource dataSource,
            @Value("#{stepExecutionContext['batchId']}") Integer batchId) {
        return new JdbcCursorItemReaderBuilder<StagedTransaction>()
                .name("partitionedStagedReader")
                .dataSource(dataSource)
                // Step-scoped reader + batchId from the partition execution
                // context ensures each worker only sees one logical batch.
                .sql("SELECT st.id, st.account_id, st.merchant_id, st.direction, " +
                     "       st.amount_cents, st.txn_date, st.status, " +
                     "       a.status AS account_status " +
                     "FROM bank.staged_transactions st " +
                     "LEFT JOIN bank.accounts a ON st.account_id = a.account_id " +
                     "WHERE st.status = 'staged' AND st.batch_id = ?")
                .queryArguments(batchId)
                .rowMapper((rs, rowNum) -> {
                    StagedTransaction item = new StagedTransaction();
                    item.setId(rs.getInt("id"));
                    item.setAccountId(rs.getInt("account_id"));
                    int merchantId = rs.getInt("merchant_id");
                    item.setMerchantId(rs.wasNull() ? null : merchantId);
                    item.setDirection(rs.getString("direction").trim());
                    item.setAmountCents(rs.getInt("amount_cents"));
                    item.setTxnDate(rs.getString("txn_date"));
                    item.setStatus(rs.getString("status"));
                    item.setAccountStatus(rs.getString("account_status"));
                    return item;
                })
                .build();
    }

    @Bean
    public ValidationWriter validationWriter(JdbcTemplate jdbcTemplate) {
        return new ValidationWriter(jdbcTemplate);
    }

    @Bean
    public Step validateWorkerStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   JdbcCursorItemReader<StagedTransaction> partitionedStagedReader,
                                   ValidationProcessor validationProcessor,
                                   ValidationWriter validationWriter,
                                   ValidationSkipListener skipListener,
                                   ProgressListener progressListener) {
        return new StepBuilder("validateWorkerStep", jobRepository)
                .<StagedTransaction, StagedTransaction>chunk(500, transactionManager)
                .reader(partitionedStagedReader)
                .processor(validationProcessor)
                .writer(validationWriter)
                .faultTolerant()
                // Validation failures are business rejections, not step failures.
                // SkipListener owns writing rejected/error state for those records.
                .skip(ValidationException.class)
                .skipLimit(10000)
                .listener(skipListener)
                .listener(progressListener)
                .build();
    }

    @Bean
    public Step validateStep(JobRepository jobRepository,
                             Step validateWorkerStep,
                             Partitioner validatePartitioner) {

        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("validate-");
        // Keep partition parallelism modest so validation fans out by batch
        // without overwhelming the shared database in dev/test environments.
        taskExecutor.setConcurrencyLimit(4);

        return new StepBuilder("validateStep", jobRepository)
                .partitioner("validateWorkerStep", validatePartitioner)
                .step(validateWorkerStep)
                .taskExecutor(taskExecutor)
                .build();
    }

    @Bean
    public Job validateTransactionsJob(JobRepository jobRepository,
                                       Step validateSetupStep,
                                       Step validateStep,
                                       ValidateJobCompletionListener listener) {
        return new JobBuilder("validateTransactionsJob", jobRepository)
                .listener(listener)
                .start(validateSetupStep)
                .next(validateStep)
                .build();
    }
}

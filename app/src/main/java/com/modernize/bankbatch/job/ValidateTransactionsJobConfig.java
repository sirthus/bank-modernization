package com.modernize.bankbatch.job;

import com.modernize.bankbatch.listener.ValidateJobCompletionListener;
import com.modernize.bankbatch.model.StagedTransaction;
import com.modernize.bankbatch.processor.ValidationProcessor;
import com.modernize.bankbatch.writer.ValidationWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ValidateTransactionsJobConfig {

    private final AtomicInteger validateJobId = new AtomicInteger();

    @Bean
    public Tasklet validateSetupTasklet(JdbcTemplate jdbcTemplate) {
        return (contribution, chunkContext) -> {

            Integer jobId = jdbcTemplate.queryForObject(
                "INSERT INTO bank.batch_jobs (job_name, status) " +
                "VALUES ('validate_transactions', 'running') RETURNING id",
                Integer.class);

            validateJobId.set(jobId);

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
    public JdbcCursorItemReader<StagedTransaction> stagedTransactionReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<StagedTransaction>()
                .name("stagedTransactionReader")
                .dataSource(dataSource)
                .sql("SELECT st.id, st.account_id, st.merchant_id, st.direction, " +
                     "       st.amount_cents, st.txn_date, st.status, " +
                     "       a.status AS account_status " +
                     "FROM bank.staged_transactions st " +
                     "LEFT JOIN bank.accounts a ON st.account_id = a.account_id " +
                     "WHERE st.status = 'staged'")
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
        return new ValidationWriter(jdbcTemplate, validateJobId);
    }

    @Bean
    public Step validateStep(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             JdbcCursorItemReader<StagedTransaction> stagedTransactionReader,
                             ValidationProcessor validationProcessor,
                             ValidationWriter validationWriter) {
        return new StepBuilder("validateStep", jobRepository)
                .<StagedTransaction, StagedTransaction>chunk(10, transactionManager)
                .reader(stagedTransactionReader)
                .processor(validationProcessor)
                .writer(validationWriter)
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

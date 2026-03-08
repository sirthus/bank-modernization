package com.modernize.bankbatch.listener;

import com.modernize.bankbatch.exception.ValidationException;
import com.modernize.bankbatch.model.StagedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ValidationSkipListener implements SkipListener<StagedTransaction, StagedTransaction> {

    private static final Logger log = LoggerFactory.getLogger(ValidationSkipListener.class);

    private final JdbcTemplate jdbcTemplate;
    private AtomicInteger validateJobId;

    public ValidationSkipListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setValidateJobId(AtomicInteger validateJobId) {
        this.validateJobId = validateJobId;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("Skipped record during read: {}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(StagedTransaction item, Throwable t) {
        log.warn("Skipped during validation: staged_transactions.id={}, reason={}",
                item.getId(), t.getMessage());

        // Update staged record to rejected
        jdbcTemplate.update(
            "UPDATE bank.staged_transactions SET status = 'rejected' WHERE id = ?",
            item.getId());

        // Log the error
        if (validateJobId != null) {
            jdbcTemplate.update(
                "INSERT INTO bank.batch_job_errors " +
                "(batch_job_id, error_message, record_ref) VALUES (?, ?, ?)",
                validateJobId.get(),
                t.getMessage(),
                "staged_transactions.id=" + item.getId());
        }
    }

    @Override
    public void onSkipInWrite(StagedTransaction item, Throwable t) {
        log.warn("Skipped during write: staged_transactions.id={}, reason={}",
                item.getId(), t.getMessage());
    }
}

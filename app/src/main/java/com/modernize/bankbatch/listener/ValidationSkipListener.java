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
/**
 * Owns the rejected-record path for validation skips.
 *
 * The processor only decides that a record is invalid; this listener persists
 * the rejected status and error log entry after Spring Batch skips the item.
 */
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

        // ValidationProcessor throws to signal a business rejection. The writer
        // never sees skipped items, so the skip listener owns rejected status.
        jdbcTemplate.update(
            "UPDATE bank.staged_transactions SET status = 'rejected' WHERE id = ?",
            item.getId());

        // Errors are tied back to the validate_transactions job row created in
        // the setup tasklet so the audit trail matches the validation run.
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

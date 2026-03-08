package com.modernize.bankbatch.writer;

import com.modernize.bankbatch.model.StagedTransaction;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicInteger;

public class ValidationWriter implements ItemWriter<StagedTransaction> {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger batchJobId;

    public ValidationWriter(JdbcTemplate jdbcTemplate, AtomicInteger batchJobId) {
        this.jdbcTemplate = jdbcTemplate;
        this.batchJobId = batchJobId;
    }

    @Override
    public void write(Chunk<? extends StagedTransaction> items) {
        for (StagedTransaction item : items) {

            // Update the staged record status
            jdbcTemplate.update(
                "UPDATE bank.staged_transactions SET status = ? WHERE id = ?",
                item.getStatus(), item.getId());

            // Log errors for rejected records
            if ("rejected".equals(item.getStatus())) {
                jdbcTemplate.update(
                    "INSERT INTO bank.batch_job_errors " +
                    "(batch_job_id, error_message, record_ref) VALUES (?, ?, ?)",
                    batchJobId.get(),
                    item.getErrorMessage(),
                    "staged_transactions.id=" + item.getId());
            }
        }
    }
}

package com.modernize.bankbatch.writer;

import com.modernize.bankbatch.model.StagedTransaction;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostingWriter implements ItemWriter<StagedTransaction> {

    private final JdbcTemplate jdbcTemplate;

    public PostingWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(Chunk<? extends StagedTransaction> items) {
        for (StagedTransaction item : items) {

            // Insert into production transactions table
            jdbcTemplate.update(
                "INSERT INTO bank.transactions " +
                "(account_id, merchant_id, direction, amount_cents, status, description) " +
                "VALUES (?, ?, ?, ?, 'posted', 'Batch posted')",
                item.getAccountId(),
                item.getMerchantId(),
                item.getDirection(),
                item.getAmountCents());

            // Update staged record to posted
            jdbcTemplate.update(
                "UPDATE bank.staged_transactions SET status = 'posted' WHERE id = ?",
                item.getId());
        }
    }
}

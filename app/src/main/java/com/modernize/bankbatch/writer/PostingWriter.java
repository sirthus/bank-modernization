package com.modernize.bankbatch.writer;

import com.modernize.bankbatch.model.StagedTransaction;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * Writes validated staged rows into production transactions and then advances
 * the source staged rows to posted.
 */
public class PostingWriter implements ItemWriter<StagedTransaction> {

    private final JdbcTemplate jdbcTemplate;

    public PostingWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(Chunk<? extends StagedTransaction> items) {
        List<? extends StagedTransaction> list = items.getItems();

        // Mirror the staged rows into the production-facing transactions table first.
        jdbcTemplate.batchUpdate(
            "INSERT INTO bank.transactions " +
            "(account_id, merchant_id, direction, amount_cents, status, description, batch_id) " +
            "VALUES (?, ?, ?, ?, 'posted', 'Batch posted', ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    StagedTransaction item = list.get(i);
                    ps.setInt(1, item.getAccountId());
                    if (item.getMerchantId() != null) {
                        ps.setInt(2, item.getMerchantId());
                    } else {
                        ps.setNull(2, Types.INTEGER);
                    }
                    ps.setString(3, item.getDirection());
                    ps.setInt(4, item.getAmountCents());
                    ps.setInt(5, item.getBatchId());
                }

                @Override
                public int getBatchSize() {
                    return list.size();
                }
            });

        // After the production insert succeeds, advance the staged source rows
        // so reconciliation can compare posted staged records to transactions.
        jdbcTemplate.batchUpdate(
            "UPDATE bank.staged_transactions SET status = 'posted' WHERE id = ?",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setInt(1, list.get(i).getId());
                }

                @Override
                public int getBatchSize() {
                    return list.size();
                }
            });
    }
}

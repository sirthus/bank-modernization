package com.modernize.bankbatch.writer;

import com.modernize.bankbatch.model.StagedTransaction;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists the accepted side of validation.
 *
 * Rejected items never reach this writer because the skip listener handles them,
 * so this writer only advances successfully processed rows to validated.
 */
public class ValidationWriter implements ItemWriter<StagedTransaction> {

    private final JdbcTemplate jdbcTemplate;

    public ValidationWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(Chunk<? extends StagedTransaction> items) {
        List<? extends StagedTransaction> list = items.getItems();

        // Only records that survived validation reach this writer; rejected ones
        // were skipped earlier and are updated by ValidationSkipListener instead.
        jdbcTemplate.batchUpdate(
            "UPDATE bank.staged_transactions SET status = 'validated' WHERE id = ?",
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

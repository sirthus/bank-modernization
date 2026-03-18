package com.modernize.bankbatch.writer;

import com.modernize.bankbatch.model.StagedTransaction;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class ValidationWriter implements ItemWriter<StagedTransaction> {

    private final JdbcTemplate jdbcTemplate;

    public ValidationWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(Chunk<? extends StagedTransaction> items) {
        List<? extends StagedTransaction> list = items.getItems();

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

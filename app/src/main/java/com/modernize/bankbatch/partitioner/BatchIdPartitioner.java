package com.modernize.bankbatch.partitioner;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchIdPartitioner implements Partitioner {

    private final JdbcTemplate jdbcTemplate;
    private final String status;

    public BatchIdPartitioner(JdbcTemplate jdbcTemplate, String status) {
        this.jdbcTemplate = jdbcTemplate;
        this.status = status;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        List<Integer> batchIds = jdbcTemplate.queryForList(
            "SELECT DISTINCT batch_id FROM bank.staged_transactions WHERE status = ?",
            Integer.class, status);

        Map<String, ExecutionContext> partitions = new HashMap<>();

        for (Integer batchId : batchIds) {
            ExecutionContext context = new ExecutionContext();
            context.putInt("batchId", batchId);
            partitions.put("partition-batch-" + batchId, context);
        }

        return partitions;
    }
}

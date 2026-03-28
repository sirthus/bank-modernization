package com.modernize.bankbatch.partitioner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BatchIdPartitioner.
 *
 * Pure unit test — JdbcTemplate mocked. The partitioner's job is to query
 * distinct batch_ids for a given status and return one ExecutionContext
 * per batch_id. Validate and Post jobs both use this partitioner.
 *
 * COBOL analogy: this is like a file-selection paragraph that determines
 * which work units to dispatch to parallel job steps.
 */
class BatchIdPartitionerTest {

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
    }

    /**
     * Normal case: two distinct batch_ids produce two partitions, each with
     * the correct batchId in its ExecutionContext.
     */
    @Test
    void partition_withTwoBatchIds_returnsTwoPartitions() {
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), any()))
            .thenReturn(List.of(1, 2));

        BatchIdPartitioner partitioner = new BatchIdPartitioner(jdbcTemplate, "staged");
        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertThat(partitions).hasSize(2);
        assertThat(partitions).containsKey("partition-batch-1");
        assertThat(partitions).containsKey("partition-batch-2");
        assertThat(partitions.get("partition-batch-1").getInt("batchId")).isEqualTo(1);
        assertThat(partitions.get("partition-batch-2").getInt("batchId")).isEqualTo(2);
    }

    /**
     * Empty result: no staged records means no partitions. The partitioned step
     * should launch zero workers and complete successfully with zero records.
     *
     * This is the "nothing to do" case — valid when all files have already been
     * processed or the load job produced no records.
     */
    @Test
    void partition_withNoBatchIds_returnsEmptyMap() {
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), any()))
            .thenReturn(Collections.emptyList());

        BatchIdPartitioner partitioner = new BatchIdPartitioner(jdbcTemplate, "staged");
        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertThat(partitions).isEmpty();
    }

    /**
     * Status string is passed through to the query. Verifies that the partitioner
     * correctly filters by the status it was constructed with (e.g. "staged" for
     * validate, "validated" for post).
     */
    @Test
    void partition_passesStatusToQuery() {
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq("validated")))
            .thenReturn(List.of(5));

        BatchIdPartitioner partitioner = new BatchIdPartitioner(jdbcTemplate, "validated");
        partitioner.partition(4);

        verify(jdbcTemplate).queryForList(anyString(), eq(Integer.class), eq("validated"));
    }
}

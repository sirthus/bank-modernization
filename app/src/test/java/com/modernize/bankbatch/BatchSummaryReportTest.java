package com.modernize.bankbatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BatchSummaryReport.
 *
 * Pure unit test — JdbcTemplate is mocked so no database is required.
 * All five queries in print() use no bind parameters, so they match the
 * single-argument queryForList(String) / queryForMap(String) overloads.
 *
 * We verify that print() completes without exception under normal and
 * edge-case conditions.
 */
class BatchSummaryReportTest {

    private JdbcTemplate jdbcTemplate;
    private BatchSummaryReport report;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        report = new BatchSummaryReport(jdbcTemplate);
    }

    /**
     * Happy path: all queries return data.
     *
     * Uses chained thenReturn() values matching the call order in print():
     *   1st queryForList  — jobs section
     *   2nd queryForList  — batches section
     *   3rd queryForList  — errors section
     *   4th queryForList  — reconciliation section
     *
     * queryForMap is called once for the totals section.
     */
    @Test
    void print_withData_completesWithoutException() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(
            // 1: jobs
            List.of(Map.of("job_name", "load_transactions", "status", "completed",
                           "record_count", 100L, "duration_secs", 1.5)),
            // 2: batches
            List.of(Map.of("file_name", "ach_test.csv", "record_count", 50L,
                           "posted", 45L, "validated", 0L, "rejected", 5L, "still_staged", 0L)),
            // 3: errors
            List.of(Map.of("error_message", "amount must be positive", "cnt", 5L)),
            // 4: reconciliation
            List.of(Map.of("batch_id", 1, "file_name", "ach_test.csv",
                           "staged_count", 45L, "posted_count", 45L,
                           "counts_match", true, "totals_match", true))
        );

        when(jdbcTemplate.queryForMap(anyString()))
            .thenReturn(Map.of("total", 100L, "posted", 90L,
                               "validated", 0L, "rejected", 10L, "still_staged", 0L));

        assertThatNoException().isThrownBy(() -> report.print());

        // All five queries should be issued
        verify(jdbcTemplate, times(4)).queryForList(anyString());
        verify(jdbcTemplate, times(1)).queryForMap(anyString());
    }

    /**
     * Empty database: all queries return zero rows.
     *
     * Verifies that print() handles empty lists and zero-value maps gracefully
     * — no NPE from iterating empty collections or missing map keys.
     */
    @Test
    void print_withEmptyData_completesWithoutException() {
        when(jdbcTemplate.queryForList(anyString()))
            .thenReturn(Collections.emptyList());

        when(jdbcTemplate.queryForMap(anyString()))
            .thenReturn(Map.of("total", 0L, "posted", 0L,
                               "validated", 0L, "rejected", 0L, "still_staged", 0L));

        assertThatNoException().isThrownBy(() -> report.print());
    }
}

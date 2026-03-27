package com.modernize.verificationlab.collector;

import com.modernize.verificationlab.model.ActualOutput;
import com.modernize.verificationlab.model.ErrorRow;
import com.modernize.verificationlab.model.TransactionRow;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Queries the bank schema after a pipeline run and assembles an ActualOutput.
 *
 * The database is cleaned before each verification test (@BeforeEach), so
 * queries do not need to scope by run ID — there is only ever one run's data
 * in the database at query time.
 *
 * All queries target the bank schema which contains the pipeline's business
 * outputs. Spring Batch's own metadata tables (BATCH_JOB_INSTANCE, etc.)
 * are not queried here.
 */
public class ActualOutputCollector {

    private ActualOutputCollector() {}

    public static ActualOutput collect(JdbcTemplate jdbc) {
        ActualOutput output = new ActualOutput();

        output.setStagedCount(count(jdbc,
            "SELECT COUNT(*) FROM bank.staged_transactions"));

        output.setPostedCount(count(jdbc,
            "SELECT COUNT(*) FROM bank.staged_transactions WHERE status = 'posted'"));

        output.setRejectedCount(count(jdbc,
            "SELECT COUNT(*) FROM bank.staged_transactions WHERE status = 'rejected'"));

        output.setErrorCount(count(jdbc,
            "SELECT COUNT(*) FROM bank.batch_job_errors"));

        output.setTotalPostedCents(sumCents(jdbc,
            "SELECT COALESCE(SUM(amount_cents), 0) FROM bank.transactions"));

        // Reconciliation flags: what the pipeline's own reconcile job recorded.
        // Returns true/false from batch_reconciliations; defaults to true if no row exists.
        output.setReconciliationCountsMatch(reconciliationFlag(jdbc, "counts_match"));
        output.setReconciliationTotalsMatch(reconciliationFlag(jdbc, "totals_match"));

        output.setTransactions(collectTransactions(jdbc));
        output.setErrors(collectErrors(jdbc));

        return output;
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer result = jdbc.queryForObject(sql, Integer.class);
        return result != null ? result : 0;
    }

    private static long sumCents(JdbcTemplate jdbc, String sql) {
        Long result = jdbc.queryForObject(sql, Long.class);
        return result != null ? result : 0L;
    }

    private static boolean reconciliationFlag(JdbcTemplate jdbc, String column) {
        List<Boolean> results = jdbc.queryForList(
            "SELECT " + column + " FROM bank.batch_reconciliations", Boolean.class);
        // If multiple batches (shouldn't happen in tests), all must match
        return results.isEmpty() || results.stream().allMatch(Boolean.TRUE::equals);
    }

    private static List<TransactionRow> collectTransactions(JdbcTemplate jdbc) {
        return jdbc.query(
            "SELECT account_id, merchant_id, direction, amount_cents, description FROM bank.transactions",
            (rs, rowNum) -> new TransactionRow(
                rs.getInt("account_id"),
                rs.getObject("merchant_id", Integer.class),
                rs.getString("direction"),
                rs.getLong("amount_cents"),
                rs.getString("description")
            )
        );
    }

    private static List<ErrorRow> collectErrors(JdbcTemplate jdbc) {
        return jdbc.query(
            "SELECT error_message, record_ref FROM bank.batch_job_errors",
            (rs, rowNum) -> new ErrorRow(
                rs.getString("error_message"),
                rs.getString("record_ref")
            )
        );
    }
}

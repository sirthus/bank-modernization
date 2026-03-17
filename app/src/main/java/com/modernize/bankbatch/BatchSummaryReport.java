package com.modernize.bankbatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BatchSummaryReport {

    private static final Logger log = LoggerFactory.getLogger(BatchSummaryReport.class);

    private final JdbcTemplate jdbcTemplate;

    public BatchSummaryReport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Prints and saves a cumulative summary report covering all pipeline runs
     * recorded in the database.
     */
    public void print() {
        List<String> lines = new ArrayList<>();

        lines.add("========================================================");
        lines.add("  BATCH PROCESSING SUMMARY REPORT");
        lines.add("  Generated: " + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        lines.add("========================================================");

        // Cumulative totals per job type across all runs
        List<Map<String, Object>> jobs = jdbcTemplate.queryForList(
            "SELECT job_name, " +
            "       'completed' AS status, " +
            "       SUM(record_count) AS record_count, " +
            "       SUM(EXTRACT(EPOCH FROM (finished_at - started_at))) AS duration_secs " +
            "FROM bank.batch_jobs " +
            "WHERE status = 'completed' " +
            "GROUP BY job_name " +
            "ORDER BY MIN(id)");

        lines.add("");
        lines.add("  JOBS:");
        for (Map<String, Object> job : jobs) {
            Object durationObj = job.get("duration_secs");
            String duration = durationObj != null
                ? String.format("%.1fs", ((Number) durationObj).doubleValue())
                : "n/a";
            lines.add(String.format("    %-25s  %s  (%s records, %s)",
                job.get("job_name"), job.get("status"),
                job.get("record_count"), duration));
        }

        // All inbound files across all runs
        List<Map<String, Object>> batches = jdbcTemplate.queryForList(
            "SELECT tb.file_name, tb.record_count, " +
            "       count(CASE WHEN st.status = 'posted' THEN 1 END) AS posted, " +
            "       count(CASE WHEN st.status = 'validated' THEN 1 END) AS validated, " +
            "       count(CASE WHEN st.status = 'rejected' THEN 1 END) AS rejected, " +
            "       count(CASE WHEN st.status = 'staged' THEN 1 END) AS still_staged " +
            "FROM bank.transaction_batches tb " +
            "LEFT JOIN bank.staged_transactions st ON st.batch_id = tb.id " +
            "GROUP BY tb.id, tb.file_name, tb.record_count " +
            "ORDER BY tb.id");

        lines.add("");
        lines.add("  INBOUND FILES:");
        for (Map<String, Object> b : batches) {
            lines.add(String.format("    %-24s  %s total: %s posted, %s rejected",
                b.get("file_name"), b.get("record_count"),
                b.get("posted"), b.get("rejected")));
        }

        // Totals across all runs
        Map<String, Object> totals = jdbcTemplate.queryForMap(
            "SELECT count(*) AS total, " +
            "       count(CASE WHEN status = 'posted' THEN 1 END) AS posted, " +
            "       count(CASE WHEN status = 'validated' THEN 1 END) AS validated, " +
            "       count(CASE WHEN status = 'rejected' THEN 1 END) AS rejected, " +
            "       count(CASE WHEN status = 'staged' THEN 1 END) AS still_staged " +
            "FROM bank.staged_transactions");

        lines.add("");
        lines.add("  TOTALS:");
        lines.add("    Staged records:    " + totals.get("total"));
        lines.add("    Posted:            " + totals.get("posted"));
        lines.add("    Rejected:          " + totals.get("rejected"));
        lines.add("    Still staged:      " + totals.get("still_staged"));

        // Errors across all runs
        List<Map<String, Object>> errors = jdbcTemplate.queryForList(
            "SELECT error_message, count(*) AS cnt " +
            "FROM bank.batch_job_errors " +
            "GROUP BY error_message " +
            "ORDER BY cnt DESC");

        lines.add("");
        lines.add("  REJECTION REASONS:");
        if (errors.isEmpty()) {
            lines.add("    (none)");
        } else {
            for (Map<String, Object> e : errors) {
                lines.add(String.format("    %6s - %s", e.get("cnt"), e.get("error_message")));
            }
        }

        // Reconciliation results across all runs
        List<Map<String, Object>> recon = jdbcTemplate.queryForList(
            "SELECT br.batch_id, tb.file_name, " +
            "       br.staged_count, br.posted_count, " +
            "       br.counts_match, br.totals_match " +
            "FROM bank.batch_reconciliations br " +
            "JOIN bank.transaction_batches tb ON tb.id = br.batch_id " +
            "ORDER BY br.id");

        lines.add("");
        lines.add("  RECONCILIATION:");
        if (recon.isEmpty()) {
            lines.add("    (none)");
        } else {
            for (Map<String, Object> r : recon) {
                String match = (Boolean) r.get("counts_match") && (Boolean) r.get("totals_match")
                    ? "PASS" : "FAIL";
                lines.add(String.format("    %-24s  staged: %s, posted: %s, result: %s",
                    r.get("file_name"), r.get("staged_count"),
                    r.get("posted_count"), match));
            }
        }

        lines.add("");
        lines.add("========================================================");

        // Print to console
        for (String line : lines) {
            log.info(line);
        }

        // Write to file
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "batch_report_" + timestamp + ".txt";
        String filePath = "reports/" + fileName;

        try {
            new java.io.File("reports").mkdirs();
            PrintWriter pw = new PrintWriter(new FileWriter(filePath));
            for (String line : lines) {
                pw.println(line);
            }
            pw.close();
            log.info("Report saved to: {}", filePath);
        } catch (IOException e) {
            log.warn("Could not write report file: {}", e.getMessage());
        }
    }
}

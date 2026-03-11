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
import java.util.Date;
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
     * Prints and saves a summary report scoped to the current pipeline run.
     *
     * All queries filter to jobs whose started_at >= runSince, which is the
     * timestamp captured at the start of BatchPipelineService.run(). This
     * ensures the report reflects only the current run even if the database
     * contains history from previous runs.
     */
    public void print(Date runSince) {
        List<String> lines = new ArrayList<>();

        lines.add("========================================================");
        lines.add("  BATCH PROCESSING SUMMARY REPORT");
        lines.add("  Generated: " + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        lines.add("========================================================");

        // Jobs from this run only
        List<Map<String, Object>> jobs = jdbcTemplate.queryForList(
            "SELECT job_name, status, record_count, " +
            "       EXTRACT(EPOCH FROM (finished_at - started_at)) AS duration_secs " +
            "FROM bank.batch_jobs " +
            "WHERE started_at >= ? " +
            "ORDER BY id",
            runSince);

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

        // Inbound files loaded during this run
        List<Map<String, Object>> batches = jdbcTemplate.queryForList(
            "SELECT tb.file_name, tb.record_count, " +
            "       count(CASE WHEN st.status = 'posted' THEN 1 END) AS posted, " +
            "       count(CASE WHEN st.status = 'validated' THEN 1 END) AS validated, " +
            "       count(CASE WHEN st.status = 'rejected' THEN 1 END) AS rejected, " +
            "       count(CASE WHEN st.status = 'staged' THEN 1 END) AS still_staged " +
            "FROM bank.transaction_batches tb " +
            "LEFT JOIN bank.staged_transactions st ON st.batch_id = tb.id " +
            "WHERE tb.batch_job_id IN " +
            "      (SELECT id FROM bank.batch_jobs WHERE started_at >= ?) " +
            "GROUP BY tb.id, tb.file_name, tb.record_count " +
            "ORDER BY tb.id",
            runSince);

        lines.add("");
        lines.add("  INBOUND FILES:");
        for (Map<String, Object> b : batches) {
            lines.add(String.format("    %-24s  %s total: %s posted, %s rejected",
                b.get("file_name"), b.get("record_count"),
                b.get("posted"), b.get("rejected")));
        }

        // Totals scoped to staged records from this run's batches
        Map<String, Object> totals = jdbcTemplate.queryForMap(
            "SELECT count(*) AS total, " +
            "       count(CASE WHEN status = 'posted' THEN 1 END) AS posted, " +
            "       count(CASE WHEN status = 'validated' THEN 1 END) AS validated, " +
            "       count(CASE WHEN status = 'rejected' THEN 1 END) AS rejected, " +
            "       count(CASE WHEN status = 'staged' THEN 1 END) AS still_staged " +
            "FROM bank.staged_transactions " +
            "WHERE batch_id IN ( " +
            "      SELECT tb.id FROM bank.transaction_batches tb " +
            "      JOIN bank.batch_jobs bj ON tb.batch_job_id = bj.id " +
            "      WHERE bj.started_at >= ?)",
            runSince);

        lines.add("");
        lines.add("  TOTALS:");
        lines.add("    Staged records:    " + totals.get("total"));
        lines.add("    Posted:            " + totals.get("posted"));
        lines.add("    Rejected:          " + totals.get("rejected"));
        lines.add("    Still staged:      " + totals.get("still_staged"));

        // Errors logged during this run's validate job
        List<Map<String, Object>> errors = jdbcTemplate.queryForList(
            "SELECT error_message, count(*) AS cnt " +
            "FROM bank.batch_job_errors " +
            "WHERE batch_job_id IN " +
            "      (SELECT id FROM bank.batch_jobs WHERE started_at >= ?) " +
            "GROUP BY error_message " +
            "ORDER BY cnt DESC",
            runSince);

        lines.add("");
        lines.add("  REJECTION REASONS:");
        if (errors.isEmpty()) {
            lines.add("    (none)");
        } else {
            for (Map<String, Object> e : errors) {
                lines.add(String.format("    %6s - %s", e.get("cnt"), e.get("error_message")));
            }
        }

        // Reconciliation results from this run
        List<Map<String, Object>> recon = jdbcTemplate.queryForList(
            "SELECT br.batch_id, tb.file_name, " +
            "       br.staged_count, br.posted_count, " +
            "       br.counts_match, br.totals_match " +
            "FROM bank.batch_reconciliations br " +
            "JOIN bank.transaction_batches tb ON tb.id = br.batch_id " +
            "WHERE br.batch_job_id IN " +
            "      (SELECT id FROM bank.batch_jobs WHERE started_at >= ?) " +
            "ORDER BY br.id",
            runSince);

        lines.add("");
        lines.add("  RECONCILIATION:");
        for (Map<String, Object> r : recon) {
            String match = (Boolean) r.get("counts_match") && (Boolean) r.get("totals_match")
                ? "PASS" : "FAIL";
            lines.add(String.format("    %-24s  staged: %s, posted: %s, result: %s",
                r.get("file_name"), r.get("staged_count"),
                r.get("posted_count"), match));
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

package com.modernize.contractreplay.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernize.contractreplay.model.ContractResult;
import com.modernize.contractreplay.model.ContractStatus;
import com.modernize.contractreplay.model.ContractViolation;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Checks the six post-run reconciliation invariants defined in
 * contracts/output/reconciliation-invariants.json against a live database.
 *
 * Each invariant maps to one SQL check against the reconciliation rows for the
 * batch_ids under examination.
 * All violations are classified as FAIL — these are structural guarantees,
 * not advisory warnings.
 *
 * INV-001  counts_match = true after a clean run (no rejected records)
 * INV-002  totals_match = true after a clean run (no rejected records)
 * INV-003  staged_count and staged_total_cents exclude rejected records
 * INV-004  each batch_id has exactly one reconciliation row
 * INV-005  staged_count and posted_count are both >= 0
 * INV-006  posted_count <= staged_count
 *
 * Call check() after runFullPipeline() completes for the test run under examination
 * and pass the exact batch_ids created by that run. This keeps old reconciliation
 * rows from poisoning a later clean check.
 */
public class OutputStateInvariantChecker {

    private static final String CONTRACT_ID = "OUTPUT-RECON-001";
    private static final String CONTRACT_PATH = "contracts/output/reconciliation-invariants.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, InvariantDefinition> INVARIANTS = loadInvariantDefinitions();

    private final JdbcTemplate jdbc;
    private final List<Integer> batchIds;

    public OutputStateInvariantChecker(JdbcTemplate jdbc, List<Integer> batchIds) {
        Objects.requireNonNull(batchIds, "batchIds must not be null");
        for (int i = 0; i < batchIds.size(); i++) {
            if (batchIds.get(i) == null) {
                throw new IllegalArgumentException(
                    "batchIds must not contain null elements — found null at index " + i);
            }
        }
        this.jdbc = jdbc;
        this.batchIds = List.copyOf(batchIds);
    }

    public ContractResult check() {
        ContractResult result = new ContractResult(CONTRACT_ID, boundaryName());

        if (batchIds.isEmpty()) {
            result.addViolation(new ContractViolation(
                "OUTPUT-RECON-SETUP",
                "No batch_ids were supplied for invariant checking",
                ContractStatus.FAIL,
                "at least one batch_id in scope",
                "batch_ids = []"));
            return result;
        }

        checkInv001(result);
        checkInv002(result);
        checkInv003(result);
        checkInv004(result);
        checkInv005(result);
        checkInv006(result);

        return result;
    }

    /**
     * INV-001: counts_match must be true for all reconciliation rows in a clean run.
     * A clean run has no rejected records — staged count == posted count.
     */
    private void checkInv001(ContractResult result) {
        int falseCount = Objects.requireNonNullElse(jdbc.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations br " +
            "WHERE " + batchScope("br") + " AND br.counts_match = false",
            Integer.class,
            scopeArgs()), 0);

        if (falseCount > 0) {
            InvariantDefinition invariant = invariant("INV-001");
            result.addViolation(new ContractViolation(
                invariant.id(),
                invariant.description() + " — counts_match = false in " + falseCount + " reconciliation row(s)",
                invariant.severity(),
                "counts_match = true",
                "counts_match = false in " + falseCount + " row(s)"));
        }
    }

    /**
     * INV-002: totals_match must be true for all reconciliation rows in a clean run.
     * staged_total_cents must equal posted_total_cents when no records are rejected.
     */
    private void checkInv002(ContractResult result) {
        int falseCount = Objects.requireNonNullElse(jdbc.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations br " +
            "WHERE " + batchScope("br") + " AND br.totals_match = false",
            Integer.class,
            scopeArgs()), 0);

        if (falseCount > 0) {
            InvariantDefinition invariant = invariant("INV-002");
            result.addViolation(new ContractViolation(
                invariant.id(),
                invariant.description() + " — totals_match = false in " + falseCount + " reconciliation row(s)",
                invariant.severity(),
                "totals_match = true",
                "totals_match = false in " + falseCount + " row(s)"));
        }
    }

    /**
     * INV-003: staged_count and staged_total_cents must reflect only
     * records with status='posted'.
     * The reconcile job queries staged_transactions WHERE status='posted',
     * so rejected records must not inflate the staged_count or staged_total_cents.
     *
     * Checked by comparing reconciliation columns against the actual posted-only
     * count and sum from staged_transactions for each batch.
     */
    private void checkInv003(ContractResult result) {
        int countMismatchCount = Objects.requireNonNullElse(jdbc.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations br " +
            "WHERE " + batchScope("br") + " AND br.staged_count != (" +
            "  SELECT COUNT(*) FROM bank.staged_transactions st " +
            "  WHERE st.batch_id = br.batch_id AND st.status = 'posted'" +
            ")",
            Integer.class,
            scopeArgs()), 0);

        if (countMismatchCount > 0) {
            InvariantDefinition invariant = invariant("INV-003");
            result.addViolation(new ContractViolation(
                "INV-003-COUNT",
                invariant.description() + " — staged_count mismatch in " + countMismatchCount + " row(s)",
                invariant.severity(),
                "staged_count = COUNT(staged_transactions WHERE status='posted')",
                "staged_count mismatch in " + countMismatchCount + " row(s)"));
        }

        int totalMismatchCount = Objects.requireNonNullElse(jdbc.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations br " +
            "WHERE " + batchScope("br") + " AND br.staged_total_cents != (" +
            "  SELECT COALESCE(SUM(st.amount_cents), 0) FROM bank.staged_transactions st " +
            "  WHERE st.batch_id = br.batch_id AND st.status = 'posted'" +
            ")",
            Integer.class,
            scopeArgs()), 0);

        if (totalMismatchCount > 0) {
            InvariantDefinition invariant = invariant("INV-003");
            result.addViolation(new ContractViolation(
                "INV-003-TOTAL",
                invariant.description() + " — staged_total_cents mismatch in " + totalMismatchCount + " row(s)",
                invariant.severity(),
                "staged_total_cents = SUM(staged_transactions.amount_cents WHERE status='posted')",
                "staged_total_cents mismatch in " + totalMismatchCount + " row(s)"));
        }
    }

    /**
     * INV-004: each batch_id must appear exactly once in batch_reconciliations.
     * Duplicate reconciliation rows would indicate a retry or idempotency failure.
     */
    private void checkInv004(ContractResult result) {
        int duplicateCount = Objects.requireNonNullElse(jdbc.queryForObject(
            "SELECT COUNT(*) FROM (" +
            "  SELECT br.batch_id, COUNT(*) AS c " +
            "  FROM bank.batch_reconciliations br " +
            "  WHERE " + batchScope("br") +
            "  GROUP BY br.batch_id HAVING COUNT(*) > 1" +
            ") duplicates",
            Integer.class,
            scopeArgs()), 0);

        if (duplicateCount > 0) {
            InvariantDefinition invariant = invariant("INV-004");
            result.addViolation(new ContractViolation(
                invariant.id(),
                invariant.description() + " — " + duplicateCount + " batch_id(s) have more than one reconciliation row",
                invariant.severity(),
                "1 row per batch_id",
                "duplicate rows for " + duplicateCount + " batch_id(s)"));
        }
    }

    /**
     * INV-005: staged_count and posted_count must both be >= 0.
     * Negative counts indicate a data corruption or incorrect aggregation.
     */
    private void checkInv005(ContractResult result) {
        int negativeCount = Objects.requireNonNullElse(jdbc.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations br " +
            "WHERE " + batchScope("br") + " AND (br.staged_count < 0 OR br.posted_count < 0)",
            Integer.class,
            scopeArgs()), 0);

        if (negativeCount > 0) {
            InvariantDefinition invariant = invariant("INV-005");
            result.addViolation(new ContractViolation(
                invariant.id(),
                invariant.description() + " — " + negativeCount + " reconciliation row(s) have negative counts",
                invariant.severity(),
                "staged_count >= 0 AND posted_count >= 0",
                "negative count in " + negativeCount + " row(s)"));
        }
    }

    /**
     * INV-006: posted_count must never exceed staged_count.
     * You cannot post more records than were staged as 'posted'.
     */
    private void checkInv006(ContractResult result) {
        int violationCount = Objects.requireNonNullElse(jdbc.queryForObject(
            "SELECT COUNT(*) FROM bank.batch_reconciliations br " +
            "WHERE " + batchScope("br") + " AND br.posted_count > br.staged_count",
            Integer.class,
            scopeArgs()), 0);

        if (violationCount > 0) {
            InvariantDefinition invariant = invariant("INV-006");
            result.addViolation(new ContractViolation(
                invariant.id(),
                invariant.description() + " — " + violationCount + " reconciliation row(s) have posted_count > staged_count",
                invariant.severity(),
                "posted_count <= staged_count",
                "posted_count exceeds staged_count in " + violationCount + " row(s)"));
        }
    }

    private static Map<String, InvariantDefinition> loadInvariantDefinitions() {
        try (InputStream is = OutputStateInvariantChecker.class.getClassLoader().getResourceAsStream(CONTRACT_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Invariant contract not found on classpath: " + CONTRACT_PATH);
            }

            JsonNode root = MAPPER.readTree(is);
            Map<String, InvariantDefinition> definitions = new LinkedHashMap<>();
            for (JsonNode node : root.path("invariants")) {
                String id = node.path("id").asText();
                definitions.put(id, new InvariantDefinition(
                    id,
                    node.path("description").asText(),
                    ContractStatus.valueOf(node.path("severity").asText("FAIL"))));
            }
            return definitions;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load invariant metadata from " + CONTRACT_PATH, e);
        }
    }

    private String boundaryName() {
        return "Output State — Reconciliation Invariants";
    }

    private InvariantDefinition invariant(String id) {
        InvariantDefinition definition = INVARIANTS.get(id);
        if (definition == null) {
            throw new IllegalStateException("Missing invariant metadata for " + id);
        }
        return definition;
    }

    private String batchScope(String alias) {
        return alias + ".batch_id IN (" + placeholders(batchIds.size()) + ")";
    }

    private Object[] scopeArgs() {
        return batchIds.toArray();
    }

    private String placeholders(int count) {
        List<String> placeholders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            placeholders.add("?");
        }
        return String.join(", ", placeholders);
    }

    private record InvariantDefinition(String id, String description, ContractStatus severity) {
    }
}

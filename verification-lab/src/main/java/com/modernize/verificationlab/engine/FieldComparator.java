package com.modernize.verificationlab.engine;

import com.modernize.verificationlab.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compares ActualOutput against BaselineOutput field by field.
 *
 * For each field that differs, looks for a matching ApprovedDifference entry
 * and classifies the finding accordingly:
 *
 *   match found, approvedBy non-empty → APPROVED_DIFFERENCE
 *   match found, approvedBy empty     → WARNING
 *   no match                          → FAIL
 *   values equal after normalization  → PASS (counted but not stored)
 *
 * Per-transaction comparison is only performed when posted counts match.
 * If counts differ, the count FAIL already tells the story; attempting a
 * positional transaction comparison on lists of different length would
 * produce misleading field-level noise.
 */
public class FieldComparator {

    private final List<ApprovedDifference> approved;

    public FieldComparator(List<ApprovedDifference> approved) {
        this.approved = approved != null ? approved : List.of();
    }

    public Result compare(ActualOutput actual, BaselineOutput baseline) {
        List<DiscrepancyItem> items = new ArrayList<>();
        int passCount = 0;

        // ── Scalar count and total fields ────────────────────────────────────────
        passCount += compareInt(items, "stagedCount",
            baseline.getStagedCount(), actual.getStagedCount());
        passCount += compareInt(items, "postedCount",
            baseline.getPostedCount(), actual.getPostedCount());
        passCount += compareInt(items, "rejectedCount",
            baseline.getRejectedCount(), actual.getRejectedCount());
        passCount += compareInt(items, "errorCount",
            baseline.getErrorCount(), actual.getErrorCount());
        passCount += compareLong(items, "totalPostedCents",
            baseline.getTotalPostedCents(), actual.getTotalPostedCents());

        // ── Per-transaction comparison (only when counts match) ─────────────────
        if (baseline.getPostedCount() == actual.getPostedCount()) {
            List<TransactionRow> baselineTxns = NormalizationRules.normalizeTransactions(baseline.getTransactions());
            List<TransactionRow> actualTxns   = NormalizationRules.normalizeTransactions(actual.getTransactions());

            for (int i = 0; i < baselineTxns.size(); i++) {
                TransactionRow b = baselineTxns.get(i);
                TransactionRow a = actualTxns.get(i);
                String prefix = "transactions[" + i + "]";

                passCount += compareString(items, prefix + ".direction",
                    b.getDirection(), a.getDirection());
                passCount += compareLong(items, prefix + ".amountCents",
                    b.getAmountCents(), a.getAmountCents());
                passCount += compareString(items, prefix + ".description",
                    b.getDescription(), a.getDescription());
            }
        }

        // ── Error message comparison ─────────────────────────────────────────────
        List<ErrorRow> baselineErrors = NormalizationRules.normalizeErrors(baseline.getErrors());
        List<ErrorRow> actualErrors   = NormalizationRules.normalizeErrors(actual.getErrors());

        int errorPairs = Math.min(baselineErrors.size(), actualErrors.size());
        for (int i = 0; i < errorPairs; i++) {
            passCount += compareString(items, "errors[" + i + "].errorMessage",
                baselineErrors.get(i).getErrorMessage(),
                actualErrors.get(i).getErrorMessage());
        }

        return new Result(items, passCount);
    }

    // ── Private comparison helpers ───────────────────────────────────────────────

    private int compareInt(List<DiscrepancyItem> items, String field, int expected, int actual) {
        return compareLong(items, field, expected, actual);
    }

    private int compareLong(List<DiscrepancyItem> items, String field, long expected, long actual) {
        if (expected == actual) return 1;  // pass
        items.add(classify(field, String.valueOf(expected), String.valueOf(actual)));
        return 0;
    }

    private int compareString(List<DiscrepancyItem> items, String field, String expected, String actual) {
        String normalizedExpected = NormalizationRules.normalizeString(expected);
        String normalizedActual   = NormalizationRules.normalizeString(actual);
        if (Objects.equals(normalizedExpected, normalizedActual)) return 1;  // pass
        items.add(classify(field, normalizedExpected, normalizedActual));
        return 0;
    }

    /**
     * Checks whether an approved-difference entry covers this field and value pair.
     * If found and signed → APPROVED_DIFFERENCE.
     * If found and unsigned → WARNING.
     * If not found → FAIL.
     */
    private DiscrepancyItem classify(String field, String expected, String actual) {
        for (ApprovedDifference ad : approved) {
            if (!field.equals(ad.getField())) continue;
            if (!valuesMatch(ad, expected, actual)) continue;

            DiscrepancyClassification classification = ad.isSigned()
                ? DiscrepancyClassification.APPROVED_DIFFERENCE
                : DiscrepancyClassification.WARNING;

            return new DiscrepancyItem(field, expected, actual, classification, ad.getRationale());
        }
        return new DiscrepancyItem(field, expected, actual, DiscrepancyClassification.FAIL, null);
    }

    private boolean valuesMatch(ApprovedDifference ad, String expected, String actual) {
        if ("contains".equals(ad.getMatchType())) {
            String expPattern = ad.getExpectedValue();
            String actPattern = ad.getActualValue();
            return (expected == null || expPattern == null || expected.contains(expPattern))
                && (actual  == null || actPattern == null || actual.contains(actPattern));
        }
        // default: exact match
        return Objects.equals(expected, ad.getExpectedValue())
            && Objects.equals(actual,   ad.getActualValue());
    }

    // ── Result container ─────────────────────────────────────────────────────────

    public record Result(List<DiscrepancyItem> discrepancies, int passCount) {}
}

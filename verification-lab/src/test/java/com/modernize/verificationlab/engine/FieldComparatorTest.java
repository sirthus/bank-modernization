package com.modernize.verificationlab.engine;

import com.modernize.verificationlab.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.modernize.verificationlab.model.DiscrepancyClassification.*;
import static org.assertj.core.api.Assertions.assertThat;

class FieldComparatorTest {

    // ── Pass scenarios ────────────────────────────────────────────────────────────

    @Test
    void compare_allFieldsMatch_noDiscrepancies() {
        ActualOutput actual = buildActual(3, 3, 0, 0, 31500);
        BaselineOutput baseline = buildBaseline(3, 3, 0, 0, 31500);

        FieldComparator.Result result = new FieldComparator(List.of()).compare(actual, baseline);

        assertThat(result.discrepancies()).isEmpty();
        assertThat(result.passCount()).isGreaterThan(0);
    }

    @Test
    void compare_descriptionDiffersOnlyByWhitespace_passes() {
        // DS-001 scenario: baseline has "Batch  posted", modern has "Batch posted"
        ActualOutput actual = buildActualWithTransactions(
            List.of(new TransactionRow(2001, 3001, "D", 5000, "Batch posted"))
        );
        BaselineOutput baseline = buildBaselineWithTransactions(
            List.of(new TransactionRow(2001, 3001, "D", 5000, "Batch  posted"))
        );

        FieldComparator.Result result = new FieldComparator(List.of()).compare(actual, baseline);

        assertThat(result.discrepancies()).noneMatch(d -> "transactions[0].description".equals(d.getField()));
    }

    // ── Fail scenarios ────────────────────────────────────────────────────────────

    @Test
    void compare_postedCountDiffers_producesFail() {
        ActualOutput actual = buildActual(4, 4, 0, 0, 67781);
        BaselineOutput baseline = buildBaseline(4, 3, 0, 0, 67777);  // DS-003 shape

        FieldComparator.Result result = new FieldComparator(List.of()).compare(actual, baseline);

        assertThat(result.discrepancies())
            .filteredOn(d -> "postedCount".equals(d.getField()))
            .singleElement()
            .extracting(DiscrepancyItem::getClassification)
            .isEqualTo(FAIL);
    }

    @Test
    void compare_amountDiffers_noApprovalEntry_producesFail() {
        // DS-002 shape: counts match, per-record amount differs
        ActualOutput actual = buildActualWithTransactions(
            List.of(new TransactionRow(2001, 3001, "D", 10001, "Batch posted"))
        );
        BaselineOutput baseline = buildBaselineWithTransactions(
            List.of(new TransactionRow(2001, 3001, "D", 10000, "Batch posted"))
        );

        FieldComparator.Result result = new FieldComparator(List.of()).compare(actual, baseline);

        assertThat(result.discrepancies())
            .filteredOn(d -> d.getField().contains("amountCents"))
            .singleElement()
            .extracting(DiscrepancyItem::getClassification)
            .isEqualTo(FAIL);
    }

    // ── Approved difference scenarios ─────────────────────────────────────────────

    @Test
    void compare_amountDiffers_withSignedApproval_producesApprovedDifference() {
        ActualOutput actual = buildActualWithTransactions(
            List.of(new TransactionRow(2001, 3001, "D", 10001, "Batch posted"))
        );
        BaselineOutput baseline = buildBaselineWithTransactions(
            List.of(new TransactionRow(2001, 3001, "D", 10000, "Batch posted"))
        );

        ApprovedDifference ad = buildApprovedDifference(
            "transactions[0].amountCents", "10000", "10001", "exact", "dallin.r");

        FieldComparator.Result result = new FieldComparator(List.of(ad)).compare(actual, baseline);

        assertThat(result.discrepancies())
            .filteredOn(d -> d.getField().contains("amountCents"))
            .singleElement()
            .extracting(DiscrepancyItem::getClassification)
            .isEqualTo(APPROVED_DIFFERENCE);
    }

    @Test
    void compare_amountDiffers_withUnsignedApproval_producesWarning() {
        ActualOutput actual = buildActualWithTransactions(
            List.of(new TransactionRow(2001, 3001, "D", 10001, "Batch posted"))
        );
        BaselineOutput baseline = buildBaselineWithTransactions(
            List.of(new TransactionRow(2001, 3001, "D", 10000, "Batch posted"))
        );

        ApprovedDifference ad = buildApprovedDifference(
            "transactions[0].amountCents", "10000", "10001", "exact", "");  // empty approvedBy

        FieldComparator.Result result = new FieldComparator(List.of(ad)).compare(actual, baseline);

        assertThat(result.discrepancies())
            .filteredOn(d -> d.getField().contains("amountCents"))
            .singleElement()
            .extracting(DiscrepancyItem::getClassification)
            .isEqualTo(WARNING);
    }

    @Test
    void compare_containsMatchType_matchesPartialStrings() {
        // DS-003 shape: error messages differ by format, not meaning
        ActualOutput actual = buildActualWithErrors(
            List.of(new ErrorRow("account is not active; ", null))
        );
        BaselineOutput baseline = buildBaselineWithErrors(
            List.of(new ErrorRow("ERR_ACCT_INACTIVE", null))
        );

        ApprovedDifference ad = buildApprovedDifference(
            "errors[0].errorMessage", "ERR_ACCT_INACTIVE", "account is not active", "contains", "dallin.r");

        FieldComparator.Result result = new FieldComparator(List.of(ad)).compare(actual, baseline);

        assertThat(result.discrepancies())
            .filteredOn(d -> d.getField().contains("errorMessage"))
            .singleElement()
            .extracting(DiscrepancyItem::getClassification)
            .isEqualTo(APPROVED_DIFFERENCE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private ActualOutput buildActual(int staged, int posted, int rejected, int errors, long totalCents) {
        ActualOutput o = new ActualOutput();
        o.setStagedCount(staged);
        o.setPostedCount(posted);
        o.setRejectedCount(rejected);
        o.setErrorCount(errors);
        o.setTotalPostedCents(totalCents);
        o.setReconciliationCountsMatch(true);
        o.setReconciliationTotalsMatch(true);
        o.setTransactions(List.of());
        o.setErrors(List.of());
        return o;
    }

    private ActualOutput buildActualWithTransactions(List<TransactionRow> txns) {
        ActualOutput o = buildActual(txns.size(), txns.size(), 0, 0, 0);
        o.setTransactions(txns);
        return o;
    }

    private ActualOutput buildActualWithErrors(List<ErrorRow> errors) {
        ActualOutput o = buildActual(2, 1, 1, errors.size(), 0);
        o.setErrors(errors);
        return o;
    }

    private BaselineOutput buildBaseline(int staged, int posted, int rejected, int errors, long totalCents) {
        BaselineOutput b = new BaselineOutput();
        b.setDatasetId("test");
        b.setStagedCount(staged);
        b.setPostedCount(posted);
        b.setRejectedCount(rejected);
        b.setErrorCount(errors);
        b.setTotalPostedCents(totalCents);
        b.setTransactions(List.of());
        b.setErrors(List.of());
        return b;
    }

    private BaselineOutput buildBaselineWithTransactions(List<TransactionRow> txns) {
        BaselineOutput b = buildBaseline(txns.size(), txns.size(), 0, 0, 0);
        b.setTransactions(txns);
        return b;
    }

    private BaselineOutput buildBaselineWithErrors(List<ErrorRow> errors) {
        BaselineOutput b = buildBaseline(2, 1, 1, errors.size(), 0);
        b.setErrors(errors);
        return b;
    }

    private ApprovedDifference buildApprovedDifference(String field, String expected, String actual,
                                                        String matchType, String approvedBy) {
        ApprovedDifference ad = new ApprovedDifference();
        ad.setField(field);
        ad.setExpectedValue(expected);
        ad.setActualValue(actual);
        ad.setMatchType(matchType);
        ad.setApprovedBy(approvedBy);
        ad.setRationale("Test rationale");
        return ad;
    }
}

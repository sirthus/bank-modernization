package com.modernize.verificationlab.engine;

import com.modernize.verificationlab.model.ErrorRow;
import com.modernize.verificationlab.model.TransactionRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationRulesTest {

    // ── String normalization ──────────────────────────────────────────────────────

    @Test
    void normalizeString_trimsLeadingAndTrailingWhitespace() {
        assertThat(NormalizationRules.normalizeString("  hello  ")).isEqualTo("hello");
    }

    @Test
    void normalizeString_collapsesInteriorWhitespace() {
        // This is the DS-001 normalization noise scenario:
        // legacy baseline has "Batch  posted" (double space)
        assertThat(NormalizationRules.normalizeString("Batch  posted")).isEqualTo("Batch posted");
    }

    @Test
    void normalizeString_handlesNull() {
        assertThat(NormalizationRules.normalizeString(null)).isNull();
    }

    @Test
    void normalizeString_leavesCleanStringsUnchanged() {
        assertThat(NormalizationRules.normalizeString("Batch posted")).isEqualTo("Batch posted");
    }

    // ── Transaction list normalization ────────────────────────────────────────────

    @Test
    void normalizeTransactions_sortsByAccountIdThenAmountCents() {
        List<TransactionRow> unsorted = List.of(
            new TransactionRow(2003, null,  "C", 30000, "Batch posted"),
            new TransactionRow(2001, 3001,  "D", 10001, "Batch posted"),
            new TransactionRow(2001, 3001,  "D", 7778,  "Batch posted"),
            new TransactionRow(2002, 3002,  "C", 20000, "Batch posted")
        );

        List<TransactionRow> result = NormalizationRules.normalizeTransactions(unsorted);

        assertThat(result).extracting(TransactionRow::getAccountId)
            .containsExactly(2001, 2001, 2002, 2003);
        assertThat(result).extracting(TransactionRow::getAmountCents)
            .containsExactly(7778L, 10001L, 20000L, 30000L);
    }

    @Test
    void normalizeTransactions_normalizesDescriptionStrings() {
        List<TransactionRow> rows = List.of(
            new TransactionRow(2001, 3001, "D", 5000, "Batch  posted")
        );

        List<TransactionRow> result = NormalizationRules.normalizeTransactions(rows);

        assertThat(result.get(0).getDescription()).isEqualTo("Batch posted");
    }

    @Test
    void normalizeTransactions_handlesNull() {
        assertThat(NormalizationRules.normalizeTransactions(null)).isEmpty();
    }

    // ── Error list normalization ──────────────────────────────────────────────────

    @Test
    void normalizeErrors_sortsByErrorMessageAlphabetically() {
        List<ErrorRow> unsorted = List.of(
            new ErrorRow("direction must be D or C; ", "staged_transactions.id=4"),
            new ErrorRow("account not found; ",         "staged_transactions.id=3")
        );

        List<ErrorRow> result = NormalizationRules.normalizeErrors(unsorted);

        assertThat(result).extracting(ErrorRow::getErrorMessage)
            .containsExactly("account not found;", "direction must be D or C;");
    }

    @Test
    void normalizeErrors_handlesNull() {
        assertThat(NormalizationRules.normalizeErrors(null)).isEmpty();
    }
}

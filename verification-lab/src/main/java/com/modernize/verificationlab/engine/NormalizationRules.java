package com.modernize.verificationlab.engine;

import com.modernize.verificationlab.model.ErrorRow;
import com.modernize.verificationlab.model.TransactionRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies cosmetic normalization before field comparison.
 *
 * Normalization removes differences that are not meaningful to the verification
 * outcome — whitespace artifacts, insertion-order variation, encoding quirks.
 * It does not mask real value differences.
 *
 * Rules are universal: applied to both actual and baseline before comparison.
 * There is no per-dataset normalization configuration in the MVP.
 */
public class NormalizationRules {

    private NormalizationRules() {}

    /**
     * Trims leading/trailing whitespace and collapses multiple interior spaces
     * to a single space. Returns null unchanged.
     */
    public static String normalizeString(String s) {
        if (s == null) return null;
        return s.strip().replaceAll("\\s+", " ");
    }

    /**
     * Returns a new list of TransactionRows with:
     * - String fields normalized
     * - List sorted by (accountId asc, amountCents asc)
     *
     * Sorting makes comparison order-insensitive for the fields we care about.
     * Insertion order into bank.transactions is not a business-meaningful property.
     */
    public static List<TransactionRow> normalizeTransactions(List<TransactionRow> rows) {
        if (rows == null) return List.of();

        List<TransactionRow> normalized = new ArrayList<>();
        for (TransactionRow row : rows) {
            TransactionRow n = new TransactionRow(
                row.getAccountId(),
                row.getMerchantId(),
                normalizeString(row.getDirection()),
                row.getAmountCents(),
                normalizeString(row.getDescription())
            );
            normalized.add(n);
        }

        normalized.sort(Comparator
            .comparingInt(TransactionRow::getAccountId)
            .thenComparingLong(TransactionRow::getAmountCents));

        return normalized;
    }

    /**
     * Returns a new list of ErrorRows with:
     * - String fields normalized
     * - List sorted by errorMessage alphabetically
     *
     * Rejection order within a batch is not business-meaningful.
     */
    public static List<ErrorRow> normalizeErrors(List<ErrorRow> rows) {
        if (rows == null) return List.of();

        List<ErrorRow> normalized = new ArrayList<>();
        for (ErrorRow row : rows) {
            normalized.add(new ErrorRow(
                normalizeString(row.getErrorMessage()),
                normalizeString(row.getRecordRef())
            ));
        }

        normalized.sort(Comparator.comparing(
            e -> e.getErrorMessage() != null ? e.getErrorMessage() : ""
        ));

        return normalized;
    }
}

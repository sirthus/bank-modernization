package com.modernize.verificationlab.model;

/**
 * The four possible outcomes for any comparison finding.
 *
 * The overall status of a verification run is the worst-case classification
 * across all individual findings.
 *
 * Severity order (worst to best): FAIL > WARNING > APPROVED_DIFFERENCE > PASS
 *
 * Severity is encoded explicitly so that reordering or adding constants
 * cannot silently break the worstOf() comparison.
 */
public enum DiscrepancyClassification {

    PASS(0),
    APPROVED_DIFFERENCE(1),
    WARNING(2),
    FAIL(3);

    private final int severity;

    DiscrepancyClassification(int severity) {
        this.severity = severity;
    }

    /**
     * Returns whichever classification represents a worse finding.
     * Used to roll up individual findings into an overall run status.
     */
    public static DiscrepancyClassification worstOf(DiscrepancyClassification a, DiscrepancyClassification b) {
        return a.severity >= b.severity ? a : b;
    }
}

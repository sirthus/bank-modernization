package com.modernize.contractreplay.model;

/**
 * The three possible outcomes for a contract boundary check or replay scenario.
 *
 * The overall suite status is the worst-case across all individual results.
 *
 * Severity order (worst to best): FAIL > WARNING > PASS
 *
 * Severity is encoded explicitly so that reordering or adding constants
 * cannot silently break the worstOf() comparison.
 */
public enum ContractStatus {

    PASS(0),
    WARNING(1),
    FAIL(2);

    private final int severity;

    ContractStatus(int severity) {
        this.severity = severity;
    }

    /**
     * Returns whichever status represents a worse outcome.
     * Used to roll up individual findings into an overall boundary or suite status.
     */
    public static ContractStatus worstOf(ContractStatus a, ContractStatus b) {
        return a.severity >= b.severity ? a : b;
    }
}

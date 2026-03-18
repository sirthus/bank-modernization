package com.modernize.bankbatch.processor;

import com.modernize.bankbatch.exception.ValidationException;
import com.modernize.bankbatch.model.StagedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for ValidationProcessor.
 *
 * This is a pure unit test: no Spring context, no database, no network.
 * ValidationProcessor has no dependencies injected through its constructor,
 * so we instantiate it directly with "new". The test runs in milliseconds.
 *
 * COBOL analogy: this is equivalent to calling a validation paragraph in
 * isolation by setting up working storage manually, rather than submitting
 * the full JCL stream and inspecting output files.
 *
 * Pattern used throughout: Arrange / Act / Assert.
 *   Arrange — build the input record (StagedTransaction)
 *   Act     — call processor.process(item), or expect it to throw
 *   Assert  — verify status, error message, or exception details
 */
class ValidationProcessorTest {

    // The class under test. Instantiated fresh before every test method so
    // no state leaks between tests.
    private ValidationProcessor processor;

    /**
     * @BeforeEach runs before every @Test method.
     * Equivalent to a COBOL initialization paragraph that clears working storage.
     */
    @BeforeEach
    void setUp() {
        processor = new ValidationProcessor();
    }

    // -------------------------------------------------------------------------
    // Helper: builds a StagedTransaction that satisfies all four rules.
    // Tests that want to exercise one failure change exactly one field,
    // leaving the rest valid. This isolates the defect under test.
    // -------------------------------------------------------------------------

    private StagedTransaction validRecord() {
        StagedTransaction txn = new StagedTransaction();
        txn.setId(1);
        txn.setAmountCents(500);       // Rule 1: positive
        txn.setDirection("D");         // Rule 2: D or C
        txn.setAccountStatus("active"); // Rules 3 & 4: exists and is active
        return txn;
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    /**
     * A record that satisfies all four rules should be returned with
     * status "validated". The processor must not throw.
     */
    @Test
    void happyPath_validRecord_returnsValidated() throws Exception {
        StagedTransaction txn = validRecord();

        StagedTransaction result = processor.process(txn);

        // assertThat comes from AssertJ — fluent assertions that produce
        // readable failure messages (e.g. "expected 'validated' but was 'rejected'")
        assertThat(result.getStatus()).isEqualTo("validated");
        assertThat(result.getErrorMessage()).isNull();
    }

    // =========================================================================
    // Rule 1: amount_cents must be positive
    // =========================================================================

    /**
     * Boundary: zero is not positive.
     * A COBOL tester would flag this as a boundary-value case — the rule says
     * "positive", so zero must be rejected even though it's not negative.
     */
    @Test
    void rule1_zeroAmount_throwsValidationException() {
        StagedTransaction txn = validRecord();
        txn.setAmountCents(0); // boundary: the first invalid value

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> processor.process(txn)
        );

        // The exception message carries the accumulated error string.
        assertThat(ex.getMessage()).contains("amount must be positive");
        // The record itself is marked rejected before the exception is thrown.
        assertThat(txn.getStatus()).isEqualTo("rejected");
    }

    /**
     * Boundary: negative amount. Also invalid, but a separate boundary point
     * from zero. Testing both confirms the rule covers the whole invalid range.
     */
    @Test
    void rule1_negativeAmount_throwsValidationException() {
        StagedTransaction txn = validRecord();
        txn.setAmountCents(-1); // boundary: one below zero

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> processor.process(txn)
        );

        assertThat(ex.getMessage()).contains("amount must be positive");
        assertThat(txn.getStatus()).isEqualTo("rejected");
    }

    /**
     * Boundary: one cent is the first valid amount.
     * Verifies the rule does not accidentally reject the smallest legal value.
     */
    @Test
    void rule1_onecentAmount_isValid() throws Exception {
        StagedTransaction txn = validRecord();
        txn.setAmountCents(1); // boundary: the first valid value

        StagedTransaction result = processor.process(txn);

        assertThat(result.getStatus()).isEqualTo("validated");
    }

    // =========================================================================
    // Rule 2: direction must be D or C
    // =========================================================================

    /**
     * A direction value that is neither D nor C must be rejected.
     * The rule is case-sensitive: lowercase "d" would also fail (tested below).
     */
    @Test
    void rule2_invalidDirection_throwsValidationException() {
        StagedTransaction txn = validRecord();
        txn.setDirection("X"); // not D or C

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> processor.process(txn)
        );

        assertThat(ex.getMessage()).contains("direction must be D or C");
        assertThat(txn.getStatus()).isEqualTo("rejected");
    }

    /**
     * Case sensitivity: lowercase "d" is not the same as "D".
     * This matters for data sourced from systems that normalize case differently.
     * COBOL data was often uppercase by convention; modern sources may not be.
     */
    @Test
    void rule2_lowercaseDirection_throwsValidationException() {
        StagedTransaction txn = validRecord();
        txn.setDirection("d"); // lowercase — fails the equals("D") check

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> processor.process(txn)
        );

        assertThat(ex.getMessage()).contains("direction must be D or C");
    }

    /**
     * Null direction: the CSV reader could produce a null if the field is
     * missing entirely. The rule must handle this without a NullPointerException.
     */
    @Test
    void rule2_nullDirection_throwsValidationException() {
        StagedTransaction txn = validRecord();
        txn.setDirection(null); // field missing from input

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> processor.process(txn)
        );

        assertThat(ex.getMessage()).contains("direction must be D or C");
    }

    // =========================================================================
    // Rule 3: account must exist
    // =========================================================================

    /**
     * accountStatus is populated by a join against bank.accounts in the reader
     * query. If no matching account exists the join returns null, which signals
     * "account not found" to the processor.
     *
     * This is the rule that required removing the FK constraint from
     * staged_transactions.account_id: records with unknown account IDs now load
     * successfully so they can reach the validate job and fail here as designed.
     */
    @Test
    void rule3_nullAccountStatus_throwsValidationException() {
        StagedTransaction txn = validRecord();
        txn.setAccountStatus(null); // join found no matching account

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> processor.process(txn)
        );

        assertThat(ex.getMessage()).contains("account not found");
        assertThat(txn.getStatus()).isEqualTo("rejected");
    }

    // =========================================================================
    // Rule 4: account must be active
    // =========================================================================

    /**
     * An account that exists but is not active (e.g. closed, frozen) must be
     * rejected. Rule 4 only fires when accountStatus is non-null — a non-null
     * value means the account was found (Rule 3 passes), then Rule 4 checks
     * the value.
     */
    @Test
    void rule4_inactiveAccount_throwsValidationException() {
        StagedTransaction txn = validRecord();
        txn.setAccountStatus("closed"); // found but not active

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> processor.process(txn)
        );

        assertThat(ex.getMessage()).contains("account is not active");
        assertThat(txn.getStatus()).isEqualTo("rejected");
    }

    // =========================================================================
    // Multi-rule failures — error accumulation
    // =========================================================================

    /**
     * When multiple rules fail, all error messages must appear in the single
     * exception that is thrown. The processor accumulates errors in a
     * StringBuilder rather than throwing on the first failure.
     *
     * COBOL analogy: a validation paragraph that sets multiple error flags
     * before returning, so the caller can report all defects at once rather
     * than sending the record back once per error.
     *
     * This test verifies the accumulation behavior: it does NOT test every
     * possible combination (that would be combinatorial explosion). It tests
     * the mechanism with a representative two-rule failure.
     */
    @Test
    void multiRule_amountAndDirection_bothErrorsInMessage() {
        StagedTransaction txn = validRecord();
        txn.setAmountCents(0);   // Rule 1 fails
        txn.setDirection("X");   // Rule 2 fails

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> processor.process(txn)
        );

        // Both error strings must appear in the combined message.
        assertThat(ex.getMessage())
            .contains("amount must be positive")
            .contains("direction must be D or C");

        assertThat(txn.getStatus()).isEqualTo("rejected");
    }

    /**
     * Verifies that the ValidationException carries the staged transaction ID
     * so the skip listener can correlate the exception back to the record it
     * came from when writing to batch_job_errors.
     */
    @Test
    void exception_carriesStagedTransactionId() {
        StagedTransaction txn = validRecord();
        txn.setId(42);
        txn.setAmountCents(0); // trigger a failure

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> processor.process(txn)
        );

        assertThat(ex.getStagedTransactionId()).isEqualTo(42);
    }
}

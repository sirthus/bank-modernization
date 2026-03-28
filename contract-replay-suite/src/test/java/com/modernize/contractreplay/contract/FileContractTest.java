package com.modernize.contractreplay.contract;

import com.modernize.contractreplay.model.ContractResult;
import com.modernize.contractreplay.model.ContractStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that FileContractValidator correctly enforces the ACH file contract
 * definition (contracts/file/ach-file-contract.json).
 *
 * These tests do not run the pipeline. They prove that the contract spec is
 * precise enough to be machine-checked and that the validator catches each
 * class of violation (delimiter, missing column, extra column, type errors).
 * The spec itself — not just this validator — is the portfolio artifact.
 *
 * No Spring context. No database. FileContractValidator is a plain static
 * method; spinning up infrastructure here would add startup cost for zero gain.
 */
class FileContractTest {

    // Valid CSV matching the ach-file-contract.json spec exactly.
    // merchant_id is nullable in the contract, so an empty value on row 3 is legal.
    private static final String VALID_CSV =
        "account_id,merchant_id,direction,amount_cents,txn_date\n" +
        "2001,3001,D,5000,2025-03-10\n" +
        "2002,3002,C,1500,2025-03-10\n" +
        "2003,,C,25000,2025-03-10\n";

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void validFile_shouldPass() {
        ContractResult result = FileContractValidator.validate(VALID_CSV);

        assertEquals(ContractStatus.PASS, result.getOverallStatus());
        assertTrue(result.getViolations().isEmpty(),
            "Expected no violations but got: " + result.getViolations());
    }

    // ── Pre-flight checks ─────────────────────────────────────────────────────

    @Test
    void nullContent_shouldFail() {
        ContractResult result = FileContractValidator.validate(null);

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-EMPTY"));
    }

    @Test
    void blankContent_shouldFail() {
        ContractResult result = FileContractValidator.validate("   ");

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-EMPTY"));
    }

    @Test
    void tabDelimited_shouldFail() {
        String csv = "account_id\tmerchant_id\tdirection\tamount_cents\ttxn_date\n" +
                     "2001\t3001\tD\t5000\t2025-03-10\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-DELIM"));
    }

    // ── Column name checks ────────────────────────────────────────────────────

    @Test
    void missingRequiredColumn_shouldFail() {
        // txn_date column removed entirely
        String csv = "account_id,merchant_id,direction,amount_cents\n" +
                     "2001,3001,D,5000\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-COL-MISSING"));
    }

    @Test
    void renamedColumn_shouldFailMissingAndWarnExtra() {
        // amount_cents renamed to amount — contract sees it as missing+extra
        String csv = "account_id,merchant_id,direction,amount,txn_date\n" +
                     "2001,3001,D,5000,2025-03-10\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-COL-MISSING"),
            "Expected missing-column violation for amount_cents");
        assertTrue(hasViolation(result, "ACH-FILE-001-COL-EXTRA"),
            "Expected extra-column warning for amount");
    }

    @Test
    void extraColumn_shouldWarn() {
        // All required columns present, plus one extra — non-breaking drift
        String csv = "account_id,merchant_id,direction,amount_cents,txn_date,notes\n" +
                     "2001,3001,D,5000,2025-03-10,ok\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.WARNING, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-COL-EXTRA"));
    }

    @Test
    void reorderedColumns_shouldWarnColumnOrder() {
        // Contract classifies "column order changed" as WARNING — consumers relying
        // on positional reads would silently misread values even though all column
        // names are present. Swap amount_cents (contracted pos 3) and txn_date (pos 4).
        String csv = "account_id,merchant_id,direction,txn_date,amount_cents\n" +
                     "2001,3001,D,2025-03-10,5000\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.WARNING, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-COL-ORDER"),
            "Expected ACH-FILE-001-COL-ORDER violation but got: " + result.getViolations());
    }

    // ── Data row type checks ──────────────────────────────────────────────────

    @Test
    void nonIntegerAmountCents_shouldFail() {
        String csv = "account_id,merchant_id,direction,amount_cents,txn_date\n" +
                     "2001,3001,D,abc,2025-03-10\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-TYPE-AMOUNT"));
    }

    @Test
    void wrongDateFormat_shouldFail() {
        // MM/DD/YYYY instead of YYYY-MM-DD
        String csv = "account_id,merchant_id,direction,amount_cents,txn_date\n" +
                     "2001,3001,D,5000,10/03/2025\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-TYPE-DATE"));
    }

    @Test
    void multiCharDirection_shouldFail() {
        // "DR" instead of single character "D"
        String csv = "account_id,merchant_id,direction,amount_cents,txn_date\n" +
                     "2001,3001,DR,5000,2025-03-10\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-ALLOWED-DIR"));
    }

    @Test
    void invalidMerchantId_shouldFail() {
        String csv = "account_id,merchant_id,direction,amount_cents,txn_date\n" +
                     "2001,abc,D,5000,2025-03-10\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-TYPE-MERCHANT"));
    }

    @Test
    void invalidLaterRow_shouldFailAndMentionTheCsvRowNumber() {
        String csv = "account_id,merchant_id,direction,amount_cents,txn_date\n" +
                     "2001,3001,D,5000,2025-03-10\n" +
                     "2002,abc,C,1500,2025-03-10\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-TYPE-MERCHANT"));
        assertTrue(result.getViolations().stream().anyMatch(v ->
                v.getDescription().contains("row 3") || v.getActual().contains("row 3")),
            "Later-row validation should point to the physical CSV row: " + result.getViolations());
    }

    @Test
    void blankNullableMerchantId_shouldPass() {
        String csv = "account_id,merchant_id,direction,amount_cents,txn_date\n" +
                     "2001,,D,5000,2025-03-10\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.PASS, result.getOverallStatus());
        assertFalse(hasViolation(result, "ACH-FILE-001-NULL-MERCHANT"));
    }

    @Test
    void zeroAmount_shouldFailMinimumConstraint() {
        String csv = "account_id,merchant_id,direction,amount_cents,txn_date\n" +
                     "2001,3001,D,0,2025-03-10\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.FAIL, result.getOverallStatus());
        assertTrue(hasViolation(result, "ACH-FILE-001-MIN-AMOUNT"));
    }

    @Test
    void windowsCrLfFile_shouldPass() {
        String csv = "account_id,merchant_id,direction,amount_cents,txn_date\r\n" +
                     "2001,3001,D,5000,2025-03-10\r\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.PASS, result.getOverallStatus(),
            "CRLF-encoded CSV should remain valid: " + result.getViolations());
    }

    @Test
    void blankLineBetweenHeaderAndData_shouldBeIgnored() {
        String csv = "account_id,merchant_id,direction,amount_cents,txn_date\n" +
                     "\n" +
                     "2001,3001,D,5000,2025-03-10\n";

        ContractResult result = FileContractValidator.validate(csv);

        assertEquals(ContractStatus.PASS, result.getOverallStatus(),
            "Blank separator lines after the header should be ignored: " + result.getViolations());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private boolean hasViolation(ContractResult result, String ruleId) {
        return result.getViolations().stream()
                .anyMatch(v -> v.getRuleId().equals(ruleId));
    }
}

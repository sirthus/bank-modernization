package com.modernize.contractreplay.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernize.contractreplay.model.ContractResult;
import com.modernize.contractreplay.model.ContractStatus;
import com.modernize.contractreplay.model.ContractViolation;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates a CSV content string against the ACH file contract definition.
 *
 * Reads contracts/file/ach-file-contract.json from the classpath and checks:
 *   1. Delimiter — file must be comma-separated
 *   2. Header presence — first line must exist
 *   3. Header column names — must match contract exactly; extra columns are WARNING
 *   4. Data row validation — every non-blank data row checked from contract metadata
 *
 * This validator checks file SHAPE, not business rules. Business rule violations
 * (unknown account, frozen account, etc.) are the pipeline's responsibility.
 *
 * Returns a ContractResult with violations classified as FAIL (breaking) or
 * WARNING (non-breaking drift). An empty violations list means PASS.
 */
public class FileContractValidator {

    private static final String CONTRACT_PATH = "contracts/file/ach-file-contract.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ContractResult validate(String csvContent) {
        ContractResult result = new ContractResult("ACH-FILE-001", "Inbound File");

        JsonNode contract;
        try {
            InputStream is = FileContractValidator.class
                    .getClassLoader()
                    .getResourceAsStream(CONTRACT_PATH);
            if (is == null) {
                result.addViolation(new ContractViolation(
                    "ACH-FILE-001-SETUP",
                    "Contract definition file not found on classpath: " + CONTRACT_PATH,
                    ContractStatus.FAIL, CONTRACT_PATH, "not found"));
                return result;
            }
            contract = MAPPER.readTree(is);
        } catch (Exception e) {
            result.addViolation(new ContractViolation(
                "ACH-FILE-001-SETUP",
                "Failed to parse contract definition: " + e.getMessage(),
                ContractStatus.FAIL, "valid JSON", e.getMessage()));
            return result;
        }

        if (csvContent == null || csvContent.isBlank()) {
            result.addViolation(new ContractViolation(
                "ACH-FILE-001-EMPTY",
                "File is empty — header row required",
                ContractStatus.FAIL, "header row present", "empty file"));
            return result;
        }

        String[] lines = csvContent.split("\\r?\\n", -1);
        String delimiter = contract.path("delimiter").asText(",");

        // ── Check 1: delimiter ────────────────────────────────────────────────
        String firstLine = lines[0].trim();
        if (!firstLine.contains(delimiter)) {
            result.addViolation(new ContractViolation(
                "ACH-FILE-001-DELIM",
                "File does not appear to use the contracted delimiter — header does not contain '" + delimiter + "'",
                ContractStatus.FAIL, "delimiter: " + delimiter, "delimiter missing from header"));
            return result;
        }

        // ── Check 2: header presence ──────────────────────────────────────────
        String[] headerCols = firstLine.split(Pattern.quote(delimiter), -1);
        for (int i = 0; i < headerCols.length; i++) {
            headerCols[i] = headerCols[i].trim();
        }

        // ── Check 3: column names ─────────────────────────────────────────────
        JsonNode columns = contract.get("columns");
        List<String> expectedNames = new ArrayList<>();
        for (JsonNode col : columns) {
            expectedNames.add(col.get("name").asText());
        }

        // Check for missing required columns
        for (String expected : expectedNames) {
            boolean found = false;
            for (String actual : headerCols) {
                if (actual.equals(expected)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.addViolation(new ContractViolation(
                    "ACH-FILE-001-COL-MISSING",
                    "Required column missing from header: " + expected,
                    ContractStatus.FAIL,
                    "column present: " + expected,
                    "column absent"));
            }
        }

        // Check for extra columns beyond the contract (WARNING — non-breaking)
        for (String actual : headerCols) {
            if (!expectedNames.contains(actual)) {
                result.addViolation(new ContractViolation(
                    "ACH-FILE-001-COL-EXTRA",
                    "Unexpected column in header: '" + actual + "' — not in contract definition",
                    ContractStatus.WARNING,
                    "columns: " + expectedNames,
                    "extra column: " + actual));
            }
        }

        // Check column order — WARNING if any required column is not at its contracted position.
        // The contract classifies "column order changed" as WARNING (non-breaking drift) because
        // schema drift is observable without preventing parsing, but consumers relying on
        // positional reads would silently misread values.
        for (JsonNode col : columns) {
            int contractedPosition = col.get("position").asInt();
            String name = col.get("name").asText();
            int actualPosition = indexOf(headerCols, name);
            if (actualPosition >= 0 && actualPosition != contractedPosition) {
                result.addViolation(new ContractViolation(
                    "ACH-FILE-001-COL-ORDER",
                    "Column '" + name + "' is at position " + actualPosition +
                    " but contract requires position " + contractedPosition,
                    ContractStatus.WARNING,
                    "position " + contractedPosition,
                    "position " + actualPosition));
            }
        }

        // If any required columns are missing, skip data row checks — they'd be meaningless
        if (result.getOverallStatus() == ContractStatus.FAIL) {
            return result;
        }

        // ── Check 4: data row validation ──────────────────────────────────────
        if (lines.length < 2) {
            // No data rows — header-only file is structurally valid
            return result;
        }

        boolean sawDataRow = false;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].trim().isBlank()) {
                sawDataRow = true;
                String[] dataRow = splitAndTrim(lines[i], delimiter);
                int csvRowNumber = i + 1;

                if (dataRow.length < headerCols.length) {
                    result.addViolation(new ContractViolation(
                        "ACH-FILE-001-ROW-SHORT",
                        "Row " + csvRowNumber + " has fewer columns than header",
                        ContractStatus.FAIL,
                        "column count: " + headerCols.length,
                        "row " + csvRowNumber + " column count: " + dataRow.length));
                    // Fall through: validate columns that are present in the short row.
                    // The guard inside the column loop (actualPosition >= dataRow.length)
                    // skips only the columns that are literally absent.
                }

                for (JsonNode col : columns) {
                    String name = col.get("name").asText();
                    int actualPosition = indexOf(headerCols, name);
                    if (actualPosition < 0 || actualPosition >= dataRow.length) {
                        continue;
                    }
                    validateValue(result, col, dataRow[actualPosition], csvRowNumber);
                }
            }
        }

        if (!sawDataRow) {
            // Header + blank separator/trailing lines only is structurally valid.
            return result;
        }

        return result;
    }

    private static String[] splitAndTrim(String line, String delimiter) {
        String[] values = line.split(Pattern.quote(delimiter), -1);
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();
        }
        return values;
    }

    private static void validateValue(ContractResult result, JsonNode column, String rawValue, int csvRowNumber) {
        String name = column.get("name").asText();
        String value = rawValue == null ? "" : rawValue.trim();
        boolean nullable = column.path("nullable").asBoolean(false);

        if (value.isBlank()) {
            if (!nullable) {
                result.addViolation(new ContractViolation(
                    ruleId(name, "NULL"),
                    name + " must not be blank in row " + csvRowNumber,
                    ContractStatus.FAIL,
                    "non-blank value",
                    "row " + csvRowNumber + ": blank"));
            }
            return;
        }

        String type = column.path("type").asText("").toUpperCase(Locale.ROOT);
        switch (type) {
            case "INTEGER" -> validateInteger(result, column, name, value, csvRowNumber);
            case "DATE" -> validateDate(result, column, name, value, csvRowNumber);
            case "STRING" -> validateString(result, column, name, value, csvRowNumber);
            default -> {
                // Unknown types are ignored so the validator remains forward-compatible.
            }
        }
    }

    private static void validateInteger(ContractResult result, JsonNode column, String name, String value, int csvRowNumber) {
        Integer parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            result.addViolation(new ContractViolation(
                ruleId(name, "TYPE"),
                name + " is not a valid integer in row " + csvRowNumber + ": '" + value + "'",
                ContractStatus.FAIL,
                "type: INTEGER",
                "row " + csvRowNumber + " value: " + value));
            return;
        }

        if (column.path("constraints").has("minimum")) {
            JsonNode minimumNode = column.path("constraints").path("minimum");
            if (!minimumNode.isInt()) {
                result.addViolation(new ContractViolation(
                    ruleId(name, "SETUP"),
                    "Contract metadata for " + name + " has a non-integer 'minimum' constraint: " + minimumNode,
                    ContractStatus.FAIL,
                    "integer minimum constraint",
                    "minimum: " + minimumNode));
                return;
            }
            int minimum = minimumNode.asInt();
            if (parsed < minimum) {
                result.addViolation(new ContractViolation(
                    ruleId(name, "MIN"),
                    name + " is below the minimum value in row " + csvRowNumber + ": '" + value + "'",
                    ContractStatus.FAIL,
                    "minimum: " + minimum,
                    "row " + csvRowNumber + " value: " + parsed));
            }
        }
    }

    private static void validateDate(ContractResult result, JsonNode column, String name, String value, int csvRowNumber) {
        String format = column.path("format").asText("YYYY-MM-DD");
        DateTimeFormatter formatter = switch (format) {
            case "YYYY-MM-DD" -> DateTimeFormatter.ISO_LOCAL_DATE;
            default -> null;
        };

        if (formatter == null) {
            result.addViolation(new ContractViolation(
                ruleId(name, "SETUP"),
                "Unsupported date format in contract for " + name + ": " + format,
                ContractStatus.FAIL,
                "supported format metadata",
                format));
            return;
        }

        try {
            LocalDate.parse(value, formatter);
        } catch (DateTimeParseException e) {
            result.addViolation(new ContractViolation(
                ruleId(name, "TYPE"),
                name + " is not in " + format + " format in row " + csvRowNumber + ": '" + value + "'",
                ContractStatus.FAIL,
                "format: " + format,
                "row " + csvRowNumber + " value: " + value));
        }
    }

    private static void validateString(ContractResult result, JsonNode column, String name, String value, int csvRowNumber) {
        JsonNode allowedValues = column.path("allowedValues");
        if (allowedValues.isArray() && !allowedValues.isEmpty()) {
            for (JsonNode allowed : allowedValues) {
                if (value.equals(allowed.asText())) {
                    return;
                }
            }
            result.addViolation(new ContractViolation(
                ruleId(name, "ALLOWED"),
                name + " is not one of the allowed values in row " + csvRowNumber + ": '" + value + "'",
                ContractStatus.FAIL,
                "allowed values: " + allowedValues,
                "row " + csvRowNumber + " value: " + value));
        }
    }

    private static String ruleId(String columnName, String suffix) {
        return switch (columnName) {
            case "account_id" -> "ACH-FILE-001-" + suffix + "-ACCOUNT";
            case "merchant_id" -> "ACH-FILE-001-" + suffix + "-MERCHANT";
            case "direction" -> "ACH-FILE-001-" + suffix + "-DIR";
            case "amount_cents" -> "ACH-FILE-001-" + suffix + "-AMOUNT";
            case "txn_date" -> "ACH-FILE-001-" + suffix + "-DATE";
            default -> "ACH-FILE-001-" + suffix + "-" + columnName.toUpperCase(Locale.ROOT);
        };
    }

    private static int indexOf(String[] array, String target) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(target)) return i;
        }
        return -1;
    }
}

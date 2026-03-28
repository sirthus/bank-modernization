package com.modernize.contractreplay.model;

/**
 * One finding against a named contract rule.
 *
 * A violation is produced when a boundary check detects a mismatch between
 * what the contract specifies and what was actually observed. Severity is
 * either WARNING (non-breaking drift) or FAIL (breaking violation).
 *
 * ruleId ties this finding back to a specific rule in the contract definition
 * file (e.g. "ACH-FILE-001-COL-3", "INV-003") so a reader knows exactly
 * which rule was checked and failed.
 */
public class ContractViolation {

    private String ruleId;
    private String description;
    private ContractStatus severity;
    private String expected;
    private String actual;

    public ContractViolation() {}

    public ContractViolation(String ruleId, String description, ContractStatus severity,
                             String expected, String actual) {
        this.ruleId = ruleId;
        this.description = description;
        this.severity = severity;
        this.expected = expected;
        this.actual = actual;
    }

    public String getRuleId()                  { return ruleId; }
    public String getDescription()             { return description; }
    public ContractStatus getSeverity()        { return severity; }
    public String getExpected()                { return expected; }
    public String getActual()                  { return actual; }

    public void setRuleId(String ruleId)               { this.ruleId = ruleId; }
    public void setDescription(String description)     { this.description = description; }
    public void setSeverity(ContractStatus severity)   { this.severity = severity; }
    public void setExpected(String expected)           { this.expected = expected; }
    public void setActual(String actual)               { this.actual = actual; }
}

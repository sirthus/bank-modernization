package com.modernize.contractreplay.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The result of checking one boundary against its contract definition.
 *
 * overallStatus is the worst-case ContractStatus across all checks and
 * unexpected violations. If every check passes and there are no violations,
 * overallStatus is PASS.
 *
 * Used by FileContractValidator, ApiContractVerifier, and
 * OutputStateInvariantChecker — one ContractResult per boundary.
 */
public class ContractResult {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private String contractId;
    private String boundaryName;
    private ContractStatus overallStatus;
    private List<ContractCheck> checks;
    private List<ContractViolation> violations;
    private String evaluatedAt;

    public ContractResult() {
        this.checks = new ArrayList<>();
        this.violations = new ArrayList<>();
        this.overallStatus = ContractStatus.PASS;
        this.evaluatedAt = LocalDateTime.now().format(ISO);
    }

    public ContractResult(String contractId, String boundaryName) {
        this();
        this.contractId = contractId;
        this.boundaryName = boundaryName;
    }

    /**
     * Adds a check and updates overallStatus to the worst-case seen so far.
     */
    public void addCheck(ContractCheck check) {
        this.checks.add(check);
        this.overallStatus = ContractStatus.worstOf(this.overallStatus, check.getStatus());
    }

    /**
     * Adds a violation and updates overallStatus to the worst-case seen so far.
     */
    public void addViolation(ContractViolation violation) {
        this.violations.add(violation);
        this.overallStatus = ContractStatus.worstOf(this.overallStatus, violation.getSeverity());
    }

    public String getContractId()              { return contractId; }
    public String getBoundaryName()            { return boundaryName; }
    public ContractStatus getOverallStatus()   { return overallStatus; }
    public List<ContractCheck> getChecks()     { return checks; }
    public List<ContractViolation> getViolations() { return violations; }
    public String getEvaluatedAt()             { return evaluatedAt; }

    public void setContractId(String contractId)         { this.contractId = contractId; }
    public void setBoundaryName(String boundaryName)     { this.boundaryName = boundaryName; }
    public void setOverallStatus(ContractStatus status)  { this.overallStatus = status; }
    public void setChecks(List<ContractCheck> checks)    { this.checks = checks; }
    public void setViolations(List<ContractViolation> v) { this.violations = v; }
    public void setEvaluatedAt(String evaluatedAt)       { this.evaluatedAt = evaluatedAt; }
}

package com.modernize.contractreplay.model;

/**
 * One executable check performed under a contract boundary.
 *
 * Unlike a ContractViolation, a ContractCheck is recorded for both passing and
 * failing checks so the generated artifact can show exactly what was exercised.
 */
public class ContractCheck {

    private String checkId;
    private String description;
    private String expectedOutcome;
    private String actualOutcome;
    private ContractStatus status;

    public ContractCheck() {
        this.status = ContractStatus.PASS;
    }

    public ContractCheck(String checkId,
                         String description,
                         String expectedOutcome,
                         String actualOutcome,
                         ContractStatus status) {
        this.checkId = checkId;
        this.description = description;
        this.expectedOutcome = expectedOutcome;
        this.actualOutcome = actualOutcome;
        this.status = status;
    }

    public String getCheckId() {
        return checkId;
    }

    public void setCheckId(String checkId) {
        this.checkId = checkId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExpectedOutcome() {
        return expectedOutcome;
    }

    public void setExpectedOutcome(String expectedOutcome) {
        this.expectedOutcome = expectedOutcome;
    }

    public String getActualOutcome() {
        return actualOutcome;
    }

    public void setActualOutcome(String actualOutcome) {
        this.actualOutcome = actualOutcome;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public void setStatus(ContractStatus status) {
        this.status = status;
    }
}

package com.modernize.verificationlab.model;

import java.util.List;

/**
 * One reviewed and recorded acceptance of a known divergence.
 *
 * Deserialized from approved-differences/{datasetId}-approved.yml.
 *
 * Classification rules:
 * - approvedBy non-empty → APPROVED_DIFFERENCE (CI passes)
 * - approvedBy empty     → WARNING (CI passes, reviewer attention required)
 * - no entry at all      → FAIL (CI fails)
 */
public class ApprovedDifference {

    private String id;
    private String field;
    private String scope;
    private String expectedValue;
    private String actualValue;
    private String matchType;           // "exact" or "contains"
    private String classification;
    private String rationale;
    private List<String> reviewCriteria;
    private String approvedBy;
    private String approvedDate;
    private String ticket;

    public String getId()                        { return id; }
    public String getField()                     { return field; }
    public String getScope()                     { return scope; }
    public String getExpectedValue()             { return expectedValue; }
    public String getActualValue()               { return actualValue; }
    public String getMatchType()                 { return matchType; }
    public String getClassification()            { return classification; }
    public String getRationale()                 { return rationale; }
    public List<String> getReviewCriteria()      { return reviewCriteria; }
    public String getApprovedBy()                { return approvedBy; }
    public String getApprovedDate()              { return approvedDate; }
    public String getTicket()                    { return ticket; }

    public void setId(String id)                              { this.id = id; }
    public void setField(String field)                        { this.field = field; }
    public void setScope(String scope)                        { this.scope = scope; }
    public void setExpectedValue(String expectedValue)        { this.expectedValue = expectedValue; }
    public void setActualValue(String actualValue)            { this.actualValue = actualValue; }
    public void setMatchType(String matchType)                { this.matchType = matchType; }
    public void setClassification(String classification)      { this.classification = classification; }
    public void setRationale(String rationale)                { this.rationale = rationale; }
    public void setReviewCriteria(List<String> reviewCriteria){ this.reviewCriteria = reviewCriteria; }
    public void setApprovedBy(String approvedBy)              { this.approvedBy = approvedBy; }
    public void setApprovedDate(String approvedDate)          { this.approvedDate = approvedDate; }
    public void setTicket(String ticket)                      { this.ticket = ticket; }

    /** True if this entry has been signed off by a named reviewer. */
    public boolean isSigned() {
        return approvedBy != null && !approvedBy.isBlank();
    }
}

package com.modernize.verificationlab.model;

/**
 * One comparison finding — a single field that differed between baseline and actual.
 *
 * PASS findings are counted but not stored as DiscrepancyItems — they would add
 * noise to the report without adding reviewer value.
 */
public class DiscrepancyItem {

    private final String field;
    private final String expectedValue;
    private final String actualValue;
    private final DiscrepancyClassification classification;
    private final String rationale;  // non-null only for APPROVED_DIFFERENCE and WARNING

    public DiscrepancyItem(String field, String expectedValue, String actualValue,
                           DiscrepancyClassification classification, String rationale) {
        this.field = field;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.classification = classification;
        this.rationale = rationale;
    }

    public String getField()                               { return field; }
    public String getExpectedValue()                       { return expectedValue; }
    public String getActualValue()                         { return actualValue; }
    public DiscrepancyClassification getClassification()   { return classification; }
    public String getRationale()                           { return rationale; }
}

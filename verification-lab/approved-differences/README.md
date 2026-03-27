# Approved Differences

This directory contains reviewer sign-off files for accepted divergences between the legacy baseline and the modern pipeline output.

## When to add a file here

Only after a human reviewer has:
1. Investigated the specific divergence
2. Answered the review criteria documented in the dataset README
3. Confirmed the divergence is a justified correction, not a defect
4. Recorded evidence-based rationale (not an assertion)

## File naming

One file per dataset: `{datasetId}-approved.yml`

Example: `ds-002-approved.yml`, `ds-003-approved.yml`

## Entry format

```yaml
datasetId: ds-002
approvedDifferences:
  - id: DS002-AD-001
    field: transactions[0].amountCents
    scope: "accountId=2001, direction=D, amountCents_actual=10001"
    expectedValue: "10000"
    actualValue: "10001"
    matchType: exact
    classification: APPROVED_DIFFERENCE
    rationale: >
      Legacy COBOL truncated intermediate decimal calculations. Modern Java
      stores the correctly-rounded value. Delta of 1 cent confirmed immaterial.
      Upstream rounding policy change reviewed and accepted by Finance Ops.
    reviewCriteria:
      - Was the legacy truncation intentional or a known defect?
      - Is the 1-cent delta immaterial for all downstream consumers?
      - Does any regulatory requirement specify truncation over rounding?
    approvedBy: dallin.r
    approvedDate: 2026-03-26
    ticket: BANK-5101
```

## Effect on classification

| approvedBy value | Classification |
|-----------------|---------------|
| Non-empty (signed) | APPROVED_DIFFERENCE — CI passes |
| Empty string | WARNING — CI passes, reviewer attention required |
| No file entry | FAIL — CI fails |

## What should never be approved casually

- Missing records
- Changed totals without a specific documented rounding rationale
- Disposition changes (posted → rejected) without confirmed upstream evidence
- Broad rules that suppress an entire field type across all records

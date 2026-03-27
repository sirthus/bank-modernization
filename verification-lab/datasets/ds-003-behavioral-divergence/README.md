# DS-003 — Behavioral Divergence / Disposition Change

## Verification story

One record in the batch contains `direction = "d"` (lowercase). The legacy system accepted lowercase direction values through implicit case normalization and posted the transaction. The modern system enforces strict `D` or `C` per spec and rejects it. Same input record, different business outcome. The lab fails.

## Why this scenario matters

This is the hardest class of modernization finding. The systems do not disagree on formatting — they disagree on whether a record is valid. A reviewer cannot determine from the diff alone whether the modern system is right (fixing a legacy defect) or wrong (introducing a regression). The lab surfaces this for explicit human judgment.

## Input

| account_id | merchant_id | direction | amount_cents | txn_date   |
|------------|-------------|-----------|--------------|------------|
| 2001       | 3001        | D         | 5000         | 2026-01-25 |
| 2002       | 3002        | C         | 3000         | 2026-01-25 |
| 2003       | (none)      | C         | 8000         | 2026-01-25 |
| 2001       | 3001        | **d**     | 2000         | 2026-01-25 |

## Legacy baseline (explicit)

The legacy system:
- Accepted `direction = "d"` as valid input
- Normalized it to `"D"` before posting
- Recorded the transaction in the posted ledger with `direction = "D"` and `amount_cents = 2000`

This was not a formatting difference. The legacy system made an affirmative posting decision for this record.

## Modern behavior

The modern pipeline:
- Reads `direction = "d"` from the CSV
- Applies Rule 2: direction must be `D` or `C` (case-sensitive)
- Rejects the record with error `"direction must be D or C; "`
- Does not post the transaction

## Divergence

| metric | baseline (legacy) | actual (modern) |
|--------|-------------------|-----------------|
| posted count | 4 | 3 |
| rejected count | 0 | 1 |
| total posted cents | 18,000 | 16,000 |
| errors | none | `"direction must be D or C; "` |

## Expected result (default — no approval entry)

| Check | Result |
|-------|--------|
| Posted count | **Fail** (4 ≠ 3) |
| Rejected count | **Fail** (0 ≠ 1) |
| Total posted cents | **Fail** (18000 ≠ 16000) |
| Error content | **Fail** (unexpected rejection) |
| **Overall** | **Fail** |

## Review criteria

A reviewer must answer all of the following before this difference can be approved:

1. Was lowercase direction ever valid operationally in the legacy system?
2. Did the upstream feed historically send lowercase direction values?
3. Was legacy case normalization intentional business behavior or accidental leniency?
4. Would rejecting lowercase now break upstream compatibility, or does it improve correctness?

## Path to Approved Difference

If investigation confirms the upstream feed sends uppercase only and the legacy normalization was accidental, a reviewer adds a signed entry to `verification-lab/approved-differences/ds-003-approved.yml` with evidence-based rationale. The approval must be scoped to this specific field and value pattern — not a broad rule suppressing all direction validation differences.

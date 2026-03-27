# DS-002 — Monetary Precision / Accumulating Drift

## Verification story

Four records post correctly by count and business key. The legacy COBOL system truncated intermediate decimal calculations; the modern Java pipeline stores the correctly-rounded value. Three of four records carry a per-record delta of +1 or +2 cents. The aggregate total drifts by +4 cents. No approval entry exists. The lab fails.

## Why this scenario matters

Record count and key matching can look clean while monetary values are silently wrong. DS-002 proves the lab performs field-level comparison on amounts and catches accumulated drift — even when the structural shape of the output is correct.

## Input

| account_id | merchant_id | direction | amount_cents | txn_date   |
|------------|-------------|-----------|--------------|------------|
| 2001       | 3001        | D         | 10001        | 2026-01-20 |
| 2002       | 3002        | C         | 20000        | 2026-01-20 |
| 2003       | (none)      | C         | 30002        | 2026-01-20 |
| 2001       | 3001        | D         | 7778         | 2026-01-20 |

## Divergence

The legacy baseline reflects truncated amounts. The modern pipeline stores exact rounded values.

| account_id | direction | baseline (legacy) | actual (modern) | delta |
|------------|-----------|-------------------|-----------------|-------|
| 2001       | D         | 10000             | 10001           | +1    |
| 2002       | C         | 20000             | 20000           | 0     |
| 2003       | C         | 30000             | 30002           | +2    |
| 2001       | D         | 7777              | 7778            | +1    |

- Aggregate baseline total: 67,777 cents
- Aggregate actual total: 67,781 cents
- Drift: +4 cents

## Expected result (default — no approval entry)

| Check | Result |
|-------|--------|
| Staged count | Pass (4 = 4) |
| Posted count | Pass (4 = 4) |
| Rejected count | Pass (0 = 0) |
| Per-record amounts | **Fail** (3 of 4 records differ) |
| Total posted cents | **Fail** (67777 ≠ 67781) |
| Reconciliation counts | Pass |
| **Overall** | **Fail** |

## Path to Approved Difference

A reviewer who investigates the rounding policy change can add a signed entry to `verification-lab/approved-differences/ds-002-approved.yml`. Once every delta has a corresponding signed approval entry, the overall result changes to Approved Difference and CI passes.

The approval entry must be narrow: scoped to the specific field, specific value range, and include evidence-based rationale — not a broad rule suppressing all amount differences.

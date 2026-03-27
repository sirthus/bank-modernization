# DS-001 — Happy Path with Normalization Noise

## Verification story

Three valid records process through the full pipeline without issue. The legacy-style baseline contains minor formatting differences that normalization resolves before comparison. The lab produces a clean pass.

## Why this scenario matters

A verification system that fails on cosmetic differences produces noise. Before you can trust a FAIL result, you need confidence that the lab distinguishes real divergence from formatting artifacts. DS-001 establishes that baseline.

## Input

| account_id | merchant_id | direction | amount_cents | txn_date   |
|------------|-------------|-----------|--------------|------------|
| 2001       | 3001        | D         | 5000         | 2026-01-15 |
| 2002       | 3002        | C         | 1500         | 2026-01-15 |
| 2003       | (none)      | C         | 25000        | 2026-01-15 |

All accounts active. All amounts positive. All directions valid.

## Normalization noise

The legacy baseline contains `"Batch  posted"` (double space) in the `description` field. The normalization rule collapses interior whitespace to a single space before comparison. After normalization, both sides read `"Batch posted"` and the comparison passes.

This is intentional — it demonstrates that the lab removes cosmetic noise without hiding real differences.

## Expected result

| Check | Result |
|-------|--------|
| Staged count | Pass (3 = 3) |
| Posted count | Pass (3 = 3) |
| Rejected count | Pass (0 = 0) |
| Total posted cents | Pass (31500 = 31500) |
| Per-record amounts | Pass |
| Description field | Pass (after normalization) |
| Reconciliation | Pass |
| **Overall** | **Pass** |

## Known accepted differences

None.

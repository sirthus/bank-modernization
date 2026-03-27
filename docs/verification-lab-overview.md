# Verification Lab — Design Overview

## Purpose

The Verification Lab is a first-party verification capability inside `bank-modernization`. Its purpose is to prove — with reproducible evidence — that the modernized Spring Batch pipeline produces outputs that are equivalent or acceptably different from legacy-style baselines.

This is not a generic comparison tool. It is a concrete modernization verification case study built to answer a specific question:

> *"How do you know the new system produces acceptable outputs?"*

---

## Verification Target

For each golden dataset, the Verification Lab asserts:

> The modernized ACH batch pipeline — running the Load, Validate, Post, and Reconcile jobs in sequence — produces outputs that match the legacy-style baseline within documented normalization rules, or differ only by explicitly reviewed and approved differences.

**In scope:**

| Output artifact | What is verified |
|----------------|-----------------|
| `bank.transactions` | Record count, business keys, per-record field values |
| `bank.staged_transactions` | Final disposition per record (posted / rejected) |
| `bank.batch_reconciliations` | Staged and posted counts and totals per batch |
| `bank.batch_job_errors` | Rejection count and error content per batch |

**Out of scope for MVP:**

- Spring Batch internal metadata tables
- Report file formatting (`reports/batch_report_*.txt`)
- Performance or throughput characteristics
- Non-ACH file types

---

## Discrepancy Classification Model

Every comparison finding lands in one of four categories. The overall result is the worst-case classification across all findings.

| Classification | Meaning | CI behavior |
|---------------|---------|-------------|
| **Pass** | No material difference detected | Continues |
| **Warning** | Notable difference; not clearly a blocking defect | Continues |
| **Fail** | Material correctness or reconciliation issue; no accepted rationale | Fails |
| **Approved Difference** | Explicitly reviewed, documented, and accepted delta | Continues |

### Classification examples

| Finding | Classification |
|---------|---------------|
| Record count matches exactly | Pass |
| Business keys present and match | Pass |
| Whitespace difference in `description` field after normalization | Pass |
| Error message format differs (same rejection decision) | Warning |
| Per-record amount differs with no approval entry | Fail |
| Missing records | Fail |
| Disposition change (posted → rejected) with signed approval entry | Approved Difference |
| Aggregate total drift with signed approval entry | Approved Difference |

---

## Normalization Rules

Normalization is applied to both actual and baseline values before comparison. Normalization removes cosmetic differences that are not meaningful to the verification outcome.

| Rule | Applied to | Behavior |
|------|-----------|---------|
| Whitespace trim | All string fields | Strip leading and trailing whitespace |
| Interior whitespace collapse | `description`, `errorMessage` | Collapse multiple spaces to one |
| Transaction list ordering | `transactions` array | Sort by `(accountId asc, amountCents asc)` |
| Error list ordering | `errors` array | Sort by `errorMessage` alphabetically |

Normalization rules are universal — they apply to every dataset. There is no per-dataset normalization configuration in the MVP. The three datasets are designed so that universal normalization is sufficient.

---

## Approved-Difference Policy

Approved differences are narrow, explicit, and reviewable. They are not broad suppression rules.

### When a difference may be approved

- An intentional modernization change with documented rationale
- A legacy defect that the modern system correctly fixes, with evidence
- A formatting or policy change that has been reviewed and confirmed non-material

### What metadata is required

Every approved-difference entry must include:

| Field | Requirement |
|-------|------------|
| `id` | Unique stable identifier (e.g., `DS002-AD-001`) |
| `field` | Dot-path of the specific field being approved |
| `scope` | Row or record identifier the approval applies to |
| `expectedOutcome` | What the legacy baseline produced |
| `actualOutcome` | What the modern system produces |
| `rationale` | Evidence-based explanation (not an assertion) |
| `reviewCriteria` | Questions the reviewer must answer before approving |
| `approvedBy` | Reviewer identifier; empty = Warning, not Approved Difference |
| `approvedDate` | ISO date of sign-off |

### What should never be casually approved

- Missing records
- Changed totals without a specific, documented rounding or policy rationale
- Disposition changes (posted → rejected) without confirmed upstream contract evidence
- Broad rules that suppress an entire field type across all records

### Approval effect on classification

| `approvedBy` value | Result |
|-------------------|--------|
| Non-empty (signed) | `APPROVED_DIFFERENCE` — CI passes |
| Empty (unsigned) | `WARNING` — CI passes but reviewer attention required |
| No entry at all | `FAIL` — CI fails |

This means an acknowledged-but-unsigned entry still surfaces the finding for human attention without blocking CI. It is not a silent suppression.

---

## MVP Datasets

Three datasets, each representing a distinct verification story.

### DS-001 — Happy Path with Normalization Noise

**Risk covered:** False positives from cosmetic differences.

**Story:** Three valid records process cleanly. The legacy-style baseline contains minor formatting differences — a double space in the `description` field — that normalization resolves before comparison. The lab produces a clean pass.

**Pass condition:** All counts match, all values match after normalization, zero discrepancies.

**What this demonstrates:** The normalization layer removes cosmetic noise without hiding real differences.

---

### DS-002 — Monetary Precision / Accumulating Drift

**Risk covered:** Undetected per-record rounding divergence that accumulates into material total drift.

**Story:** Four records post correctly by count and business key. Legacy COBOL truncated intermediate decimal calculations; Java rounds correctly. Three of four records have a per-record amount delta of +1 or +2 cents. The aggregate total drifts by +4 cents. No approval entry exists. The lab fails.

**Pass condition (default):** None — the lab fails on unapproved monetary divergence.

**Pass condition (after review):** A reviewer investigates, confirms the rounding policy change, adds signed approval entries, and reruns. The lab then produces Approved Difference.

**What this demonstrates:**
1. The lab detects real monetary divergence at the field level and the aggregate level.
2. Without explicit sign-off, monetary differences are always a Fail.
3. The approval workflow converts a Fail to Approved Difference — but only after documented human review.

---

### DS-003 — Behavioral Divergence / Disposition Change

**Risk covered:** A modernization change that alters business outcome for a record, which may be a justified correction or an unintended regression.

**Story:** One record contains `direction = "d"` (lowercase). Legacy accepted it through implicit case normalization and posted the transaction. Modern enforces strict `D` or `C` and rejects it. The baseline expects a posted transaction; the actual output is a rejection. The lab fails.

**Baseline story (explicit):** The legacy system accepted lowercase direction values, normalized them implicitly, and posted the resulting transaction. This is not a formatting difference — it is a different business outcome for the same input record.

**Review criteria a human must answer before approving:**
1. Was lowercase direction ever valid operationally?
2. Did the upstream feed historically send lowercase values?
3. Was legacy case normalization intentional business behavior or accidental leniency?
4. Would rejecting lowercase now break upstream compatibility or improve correctness?

**Pass condition (default):** None — the lab fails on an unapproved disposition change.

**Pass condition (after review):** A reviewer investigates, confirms the upstream contract, adds a signed and scoped approval entry, and reruns. The lab produces Approved Difference.

**What this demonstrates:**
1. The lab catches outcome divergence, not just value divergence.
2. The reviewer sees both what legacy did and what modern does — not just a raw diff.
3. The approval entry encodes the investigation result as a durable artifact.

---

## Evidence Reports

Each run produces two report files in `verification-lab/reports/`:

- `verification-{datasetId}-{timestamp}.json` — machine-readable, suitable for CI artifact storage and downstream tooling
- `verification-{datasetId}-{timestamp}.html` — human-readable, designed for reviewer consumption

### Report structure

**Summary section (first screen):**
- Dataset ID and description
- Overall status as a color-coded badge
- Count table: Pass / Warning / Fail / Approved Difference
- Reconciliation summary
- Commit SHA, timestamp, environment

**Discrepancies section:**
- One row per non-Pass finding
- Columns: Field | Expected | Actual | Classification | Rationale
- Color-coded by severity
- Empty state ("No discrepancies found") for DS-001

**Approved differences section:**
- Full rationale and review criteria per entry
- Unsigned entries flagged "Awaiting sign-off"

**Metadata footer:**
- Pipeline run ID, environment, dataset scenario description

### Design principle

The first screen of the HTML report must answer the reviewer's primary question immediately: *"Did this run pass, and if not, why not?"* Detail is available by scrolling; the summary is never buried.

---

## Running the lab

```bash
# From repo root — install the app module, then run the lab
./app/mvnw -f app/pom.xml install -DskipTests
./app/mvnw -f verification-lab/pom.xml test
```

Reports land in `verification-lab/reports/` as timestamped JSON and HTML files. In CI, they are uploaded as a GitHub Actions artifact retained for 90 days. See [`verification-lab/README.md`](../verification-lab/README.md) for prerequisites and CI integration details.

---

## Baseline format

Each dataset's baseline is a JSON file at `verification-lab/expected-output/{datasetId}/baseline.json`. It is a human-authored representation of what the legacy system would have produced — not an automated snapshot.

Fields:

| Field | Type | Description |
|-------|------|-------------|
| `datasetId` | string | Matches the dataset folder name (e.g. `ds-001`) |
| `description` | string | One-line scenario description |
| `stagedCount` | int | Records loaded into `staged_transactions` |
| `postedCount` | int | Records posted to `transactions` |
| `rejectedCount` | int | Records rejected during validation |
| `errorCount` | int | Rows in `batch_job_errors` |
| `totalPostedCents` | long | Sum of `amount_cents` across all posted records |
| `reconciliation` | object | `{ "countsMatch": bool, "totalsMatch": bool }` |
| `transactions` | array | Per-record: `accountId`, `merchantId`, `direction`, `amountCents`, `description` |
| `errors` | array | Per-rejection: `errorMessage` |

DS-001's baseline illustrates the normalization noise: each transaction carries `"description": "Batch  posted"` (two spaces). After interior whitespace collapse, this matches the modern pipeline's `"Batch posted"` — a clean pass.

---

## Out of Scope (MVP)

| Item | Reason excluded |
|------|----------------|
| Web UI | Evidence reports are sufficient for human review |
| General-purpose reusable framework | This is a case study, not a product |
| Large-scale DSL or plugin architecture | Adds complexity without adding verification value |
| Full audit/compliance subsystem | Out of scope for modernization verification MVP |
| Support for every file type or schema variation | Scope is intentionally narrow |

---

## Limitations

These limitations are acknowledged and do not undermine the lab's purpose:

- **Fixed datasets** — the three golden datasets are not full production coverage. They demonstrate the verification methodology; they do not exhaustively test the pipeline.
- **Scoped normalization rules** — normalization applies only to the fields and patterns defined above. Novel cosmetic differences in future outputs would need new rules.
- **Human review remains necessary** — the lab surfaces findings and encodes review criteria. It does not replace judgment. Borderline cases require a human decision before promotion.
- **Baseline provenance** — baselines are human-authored documents representing legacy-style expected outputs. They are not automated snapshots of a running legacy system.

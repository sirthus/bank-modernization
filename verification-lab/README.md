# Verification Lab

The Verification Lab is a standalone Maven module that answers the central question of any batch pipeline modernization:

> *"How do you know the new system produces acceptable outputs?"*

It runs the full Spring Batch pipeline against fixed golden datasets, queries the resulting database state, and compares it field-by-field against legacy-style baselines. Every run produces JSON and HTML evidence reports. CI fails on unapproved divergence.

See [`docs/verification-lab-overview.md`](../docs/verification-lab-overview.md) for the design rationale and classification model, and [`docs/verification-case-study.md`](../docs/verification-case-study.md) for the engineering narrative behind the approach.

---

## How it works

| Component | Class | Role |
|-----------|-------|------|
| Output collector | `ActualOutputCollector` | Queries `bank.*` tables after the pipeline run |
| Baseline loader | `BaselineLoader` | Reads `expected-output/{datasetId}/baseline.json` from classpath |
| Normalization | `NormalizationRules` | Strips whitespace, collapses interior spaces, sorts lists deterministically |
| Comparison | `FieldComparator` | Field-by-field comparison; looks up approved-difference entries for each mismatch |
| Engine | `VerificationEngine` | Orchestrates comparison, rolls up worst-case classification |
| Reports | `HtmlReportGenerator`, `JsonReportGenerator` | Writes timestamped files to `reports/` |

---

## Datasets

| ID | Scenario | Expected result | What it proves |
|----|---------|-----------------|----------------|
| DS-001 | [Happy path / normalization noise](datasets/ds-001-happy-path-normalization/README.md) | PASS | Normalization removes cosmetic differences without hiding real ones |
| DS-002 | [Monetary precision / accumulating drift](datasets/ds-002-monetary-precision/README.md) | FAIL | Per-record rounding divergence is detected at field level and aggregate level |
| DS-003 | [Behavioral divergence / disposition change](datasets/ds-003-behavioral-divergence/README.md) | FAIL | Outcome-level differences (posted vs. rejected) are caught and surfaced for review |

DS-002 and DS-003 are designed to fail. A PASS from those tests in the absence of approval entries would indicate a false negative in the comparison engine.

---

## How to run

```bash
# Start PostgreSQL and build the schema (from repo root)
docker compose up -d
.\scripts\setup-environments.ps1

# Install the app module (produces the thin jar and test-jar the lab depends on)
./app/mvnw -f app/pom.xml install -DskipTests

# Run all verification lab tests
./app/mvnw -f verification-lab/pom.xml test
```

Reports are written to `verification-lab/reports/` as:
- `verification-{datasetId}-{timestamp}.json`
- `verification-{datasetId}-{timestamp}.html`

The `reports/` directory is git-ignored.

---

## Reading the reports

Open any `.html` file in a browser. The first screen answers the primary question immediately:

- **Status badge** — color-coded: green (PASS), amber (WARNING), red (FAIL), blue (APPROVED_DIFFERENCE)
- **Summary counts** — Warning / Fail / Approved, one number each
- **Discrepancies table** — Field | Expected (baseline) | Actual (modern) | Classification | Rationale; only non-Pass findings appear here
- **Approved differences section** — full rationale and reviewer sign-off per entry; unsigned entries flagged "Awaiting sign-off"
- **Metadata footer** — dataset ID, commit SHA, environment, timestamp

In CI, reports are uploaded as a GitHub Actions artifact (`verification-lab-reports-{sha}`) and retained for 90 days. Download them from the Actions run page to review any failing dataset without re-running locally.

---

## Adding an approved difference

When a Fail represents a justified modernization change rather than a defect, a reviewer adds an entry to `verification-lab/approved-differences/{datasetId}-approved.yml`:

```yaml
datasetId: ds-002
approvedDifferences:
  - id: DS002-AD-001
    field: totalPostedCents
    expectedValue: "67777"
    actualValue: "67781"
    delta: "+4"
    matchType: exact
    classification: APPROVED_DIFFERENCE
    rationale: >
      Legacy COBOL truncated intermediate decimal calculations; Java rounds correctly.
      Delta of 4 cents across 4 records is the expected result of the rounding fix.
      Reviewed and confirmed non-material by Finance Operations.
    approvedBy: dallin.r
    approvedDate: 2026-03-26
    ticket: BANK-4421
```

**Effect on classification:**

| `approvedBy` value | Result |
|-------------------|--------|
| Non-empty (signed) | `APPROVED_DIFFERENCE` — CI passes |
| Empty (unsigned) | `WARNING` — CI passes, reviewer attention required |
| No entry | `FAIL` — CI fails |

See [`approved-differences/README.md`](approved-differences/README.md) for the full policy.

---

## CI integration

The `verification-lab` job in `.github/workflows/maven-test.yml` runs after the `test` job (app module tests must pass first). It:

1. Spins up a fresh PostgreSQL 18 service container
2. Builds the schema using the same SQL scripts as the app test job
3. Installs the app module without tests (`install -DskipTests`)
4. Runs `./app/mvnw -f verification-lab/pom.xml test`
5. Uploads all files in `verification-lab/reports/` as artifact `verification-lab-reports-{sha}`, retained 90 days (`if: always()` — reports are uploaded even when the job fails)

**CI outcome contract:**
- DS-001 must pass (PASS status)
- DS-002 and DS-003 assert FAIL as their expected JUnit outcome — those tests pass in CI because the verification engine correctly detects the divergence; they would fail if the engine produced a false negative

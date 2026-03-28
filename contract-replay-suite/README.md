# `contract-replay-suite`

This module is a contract-and-replay QA showcase for `bank-batch`. It is meant to be readable by an external reviewer as both executable verification logic and a small evidence system: the suite enforces boundary contracts, replays operational regressions from fixtures, and emits human-readable artifacts that explain what was exercised.

## What This Suite Proves

- Inbound file compatibility is enforced from JSON contract metadata, including negative checks for missing columns, later-row type errors, extra columns, and column-order drift.
- API boundaries are treated as contracts, not just controller smoke tests: status code, body shape, and `Content-Type` are all verified against machine-readable contracts.
- Output correctness is asserted directly in the reconciliation tables, scoped to the run under examination so historical bad rows cannot poison a later clean check.
- Operational regressions are replayed from fixtures with explicit expected outcomes, expected-vs-actual summaries, and reconciliation detail rows that show why a scenario passed or failed.

## Why These Boundaries and Scenarios Were Chosen

- File contract checks catch the earliest integration failures, before malformed input can create misleading downstream state.
- API contract checks protect the thin orchestration surface that scripts and operators actually depend on.
- Output invariants verify postconditions in the database, which is where silent reconcile defects tend to surface.
- Replay scenarios focus on regressions that are easy to miss with isolated unit tests: baseline control, run isolation, and rejected-row handling.

## How Contracts, Fixtures, and Reports Relate

1. Boundary contracts live in [`src/main/resources/contracts`](src/main/resources/contracts) and define the machine-readable expectations for file shape, API responses, and reconciliation invariants.
2. Replay expectations live in [`src/test/resources/fixtures`](src/test/resources/fixtures) and describe both the intended scenario and the regression it is designed to catch.
3. [`SuiteReportTest`](src/test/java/com/modernize/contractreplay/SuiteReportTest.java) executes those contracts and replay fixtures end to end, then emits JSON and Markdown evidence through the report generators.
4. Checked-in showcase artifacts live at [`docs/examples/portfolio-sample-suite-result.md`](docs/examples/portfolio-sample-suite-result.md), [`docs/examples/portfolio-sample-suite-result.json`](docs/examples/portfolio-sample-suite-result.json), and [`docs/examples/guardrail-matrix.md`](docs/examples/guardrail-matrix.md). Fresh runtime artifacts are written to `reports/`.

## Example Failure Modes This Suite Is Designed to Catch

- A CSV whose first data row is valid but whose second row has `merchant_id=abc`.
- Returning `application/json` or a changed plain-text body from `POST /api/batch/run`.
- A reconcile bug that leaks rejected rows into `staged_count` or `staged_total_cents`.
- A run-isolation regression where the second reconcile row accidentally aggregates both runs.
- A clean run that would have failed invariant checks because an older bad reconciliation row was still present in the database.

## QA Design Choices

- Contract-driven validation: file and API assertions are loaded from machine-readable JSON instead of being duplicated as ad hoc test constants.
- Replay-first regression guards: replay fixtures describe both the expected state and the concrete regression each scenario is meant to expose.
- Scoped postconditions: reconciliation invariants are evaluated against the exact `batch_id` set produced by the run under examination.
- Positive and negative evidence: the suite report shows both expected passes and expected detections, so green output still proves that the suite would catch real drift.

## Guardrail Matrix

The table below is generated from executable metadata in the contract JSON and replay expectation fixtures. The checked-in standalone copy is [`docs/examples/guardrail-matrix.md`](docs/examples/guardrail-matrix.md).

<!-- BEGIN GENERATED GUARDRAIL MATRIX -->
| Type | ID | Description | Guards Against |
|------|----|-------------|----------------|
| API scenario | clean_trigger | Pipeline triggered with no concurrent run in progress | Changing the success response body or returning a JSON envelope instead of plain text, breaking scripts that depend on the exact completion message. |
| API scenario | concurrent_trigger | POST fired while pipeline is already running (AtomicBoolean guard active) | Dropping the 409 conflict semantics for concurrent runs, hiding orchestration collisions from callers. |
| API scenario | pipeline_failure | Pipeline throws a runtime exception (e.g. reconciliation mismatch) | Masking runtime failures behind a success-looking response or a differently shaped error body. |
| API scenario | pipeline_idle | No pipeline run is in progress | Changing the idle response string away from the exact token depended on by pollers and shell scripts. |
| API scenario | pipeline_running | Pipeline run is currently in progress (AtomicBoolean guard is true) | Changing the running response string away from the exact token depended on by pollers and shell scripts. |
| Output invariant | INV-001 | counts_match must be true after a clean run with no rejected records | A reconciliation regression that leaves counts_match=false after a clean run. |
| Output invariant | INV-002 | totals_match must be true after a clean run with no rejected records | A reconciliation regression that leaves totals_match=false after a clean run. |
| Output invariant | INV-003 | Rejected records must be excluded from staged_count and staged_total_cents | Rejected rows leaking into staged_count or staged_total_cents. |
| Output invariant | INV-004 | Each batch_id must have exactly one reconciliation row — no duplicates on retry | Duplicate reconciliation rows for the same batch after retry or rerun. |
| Output invariant | INV-005 | staged_count and posted_count must both be >= 0 | Negative reconciliation counts caused by aggregation or data-corruption bugs. |
| Output invariant | INV-006 | posted_count must never exceed staged_count — cannot post more than were staged as posted | posted_count exceeding staged_count because reconciliation over-reported posted transactions. |
| Replay scenario | RP-001 | Baseline happy path — 3 valid records, all posted, reconciliation passes | Any harness breakage, fixture drift, database setup issue, or posting/reconciliation regression would cause the baseline control case to fail. |
| Replay scenario | RP-002 | Sequential run isolation — same fixture run twice, each run scoped by run.id timestamp | Removing or broadening the started_at >= ? filter in ReconcileJobConfig would cause run 2 to include run 1's batches, inflating staged_count and posted_count to 6 in a single reconciliation row. |
| Replay scenario | RP-003 | Validation skip — bad row rejected, pipeline completes, reconciliation excludes rejected row | Changing the skip-policy limit to 0, removing ValidationException from the skippable-exception list, or removing the status='posted' filter in ReconcileJobConfig would all cause this test to fail. |
<!-- END GENERATED GUARDRAIL MATRIX -->

## How to Run It Locally

```powershell
mvn -f contract-replay-suite/pom.xml test
```

The end-to-end run writes timestamped JSON and Markdown evidence to `contract-replay-suite/reports/`. The checked-in sample artifacts under `docs/examples/` are deterministic snapshots of the same schema and layout, intended for asynchronous review.

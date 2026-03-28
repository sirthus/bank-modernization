# Guardrail Matrix

This table is generated from executable metadata in the API contract JSON, output invariant contract JSON, and replay expectation fixtures.

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

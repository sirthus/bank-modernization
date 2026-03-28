# Contract + Replay Suite Report

## Suite Summary

| Field | Value |
|-------|-------|
| Suite ID | contract-replay-suite |
| Timestamp | 2026-03-27T00:00:00 |
| Environment | contracttest |
| Overall Status | **PASS** |
| Contract Boundaries | 4 |
| Replay Scenarios | 3 |

## Contract Boundary Matrix

| Contract ID | Boundary | Boundary Status | Check ID | Check Description | Check Status | Expected | Actual |
|-------------|----------|-----------------|----------|-------------------|--------------|----------|--------|
| ACH-FILE-001 | Inbound File | PASS | FILE-VALID-001 | Canonical inbound fixture satisfies the ACH file contract | PASS | Validator returns PASS with no violations | PASS with no violations |
| ACH-FILE-001 | Inbound File | PASS | FILE-NEG-MISSING-COL | Missing required column is detected | PASS | Validator returns FAIL with ACH-FILE-001-COL-MISSING | FAIL with violations: ACH-FILE-001-COL-MISSING |
| ACH-FILE-001 | Inbound File | PASS | FILE-NEG-LATER-ROW-TYPE | Malformed later rows are validated, not just the first row | PASS | Validator returns FAIL with ACH-FILE-001-TYPE-MERCHANT mentioning row 3 | FAIL with violations: ACH-FILE-001-TYPE-MERCHANT |
| ACH-FILE-001 | Inbound File | PASS | FILE-NEG-EXTRA-COL | Extra columns are surfaced as non-breaking drift | PASS | Validator returns WARNING with ACH-FILE-001-COL-EXTRA | WARNING with violations: ACH-FILE-001-COL-EXTRA |
| ACH-FILE-001 | Inbound File | PASS | FILE-NEG-COL-ORDER | Column-order drift is surfaced as a warning | PASS | Validator returns WARNING with ACH-FILE-001-COL-ORDER | WARNING with violations: ACH-FILE-001-COL-ORDER |
| API-BATCH-RUN-001 | POST /api/batch/run | PASS | API-RUN-CLEAN | Happy-path trigger response matches the contract | PASS | Scenario clean_trigger satisfies the API contract | status=200, contentType=text/plain;charset=UTF-8, body="Pipeline completed successfully" |
| API-BATCH-RUN-001 | POST /api/batch/run | PASS | API-RUN-CONCURRENT | Concurrent trigger conflict response matches the contract | PASS | Scenario concurrent_trigger satisfies the API contract | status=409, contentType=text/plain;charset=UTF-8, body="Pipeline is already running" |
| API-BATCH-RUN-001 | POST /api/batch/run | PASS | API-RUN-FAILURE | Pipeline failure response matches the contract | PASS | Scenario pipeline_failure satisfies the API contract | status=500, contentType=text/plain;charset=UTF-8, body="Pipeline failed: forced suite failure" |
| API-BATCH-STATUS-001 | GET /api/batch/status | PASS | API-STATUS-IDLE | Idle poll response matches the contract | PASS | Scenario pipeline_idle satisfies the API contract | status=200, contentType=text/plain;charset=UTF-8, body="idle" |
| API-BATCH-STATUS-001 | GET /api/batch/status | PASS | API-STATUS-RUNNING | Running poll response matches the contract | PASS | Scenario pipeline_running satisfies the API contract | status=200, contentType=text/plain;charset=UTF-8, body="running" |
| OUTPUT-RECON-001 | Output State — Reconciliation Invariants | PASS | OUTPUT-CLEAN-PASS | Clean baseline run satisfies all scoped reconciliation invariants | PASS | Scoped invariant check returns PASS with no violations | PASS with no violations |
| OUTPUT-RECON-001 | Output State — Reconciliation Invariants | PASS | OUTPUT-REJECTED-EXCLUDED | Rejected rows are excluded from INV-003 count and total calculations | PASS | Scoped invariant check passes with no INV-003 violations | PASS with no violations |
| OUTPUT-RECON-001 | Output State — Reconciliation Invariants | PASS | OUTPUT-TAMPER-DETECTED | Tampered staged totals are detected as INV-003-TOTAL | PASS | Scoped invariant check fails with INV-003-TOTAL only | FAIL with violations: INV-003-TOTAL |
| OUTPUT-RECON-001 | Output State — Reconciliation Invariants | PASS | OUTPUT-SCOPED-CLEAN | Historical bad reconciliations do not poison a later scoped clean run | PASS | Scoped invariant check returns PASS for the later clean batch_ids | PASS with no violations |

## Replay Scenario Matrix

| Scenario | Status | Purpose | Regression Guard | Expected | Actual |
|----------|--------|---------|------------------|----------|--------|
| RP-001 | PASS | Control case: proves the replay harness, Testcontainers setup, DB queries, and reporting all work end to end. If this fails, the environment is broken, not the scenarios. | Any harness breakage, fixture drift, database setup issue, or posting/reconciliation regression would cause the baseline control case to fail. | staged=3, posted=3, rejected=0, errors=0, total=31500, reconRows=1 | staged=3, posted=3, rejected=0, errors=0, total=31500, reconRows=1, countsMatch=true, totalsMatch=true, perRunMatch=n/a |
| RP-002 | PASS | Proves the reconcile job's WHERE bj.started_at >= ? filter correctly isolates each run's reconciliation. If this filter is removed or broken, run 2 would aggregate both runs' batches and produce a staged_count of 6 instead of 3. | Removing or broadening the started_at >= ? filter in ReconcileJobConfig would cause run 2 to include run 1's batches, inflating staged_count and posted_count to 6 in a single reconciliation row. | staged=6, posted=6, rejected=0, errors=0, total=63000, per-run staged=3, per-run posted=3, per-run total=31500, aggregateReconRows=2 | staged=6, posted=6, rejected=0, errors=0, total=63000, reconRows=2, countsMatch=true, totalsMatch=true, perRunMatch=true |
| RP-003 | PASS | Proves that Spring Batch's skip policy tolerates ValidationException, that rejected rows are correctly tracked in batch_job_errors, and that reconciliation's WHERE status='posted' filter excludes rejected records from staged_count. | Changing the skip-policy limit to 0, removing ValidationException from the skippable-exception list, or removing the status='posted' filter in ReconcileJobConfig would all cause this test to fail. | staged=3, posted=2, rejected=1, errors=1, total=6500, reconRows=1 | staged=3, posted=2, rejected=1, errors=1, total=6500, reconRows=1, countsMatch=true, totalsMatch=true, perRunMatch=n/a |

### RP-001 Reconciliation Details

| Run | Batch ID | Staged | Posted | Staged Total | Posted Total | Counts Match | Totals Match | Expectation Match |
|-----|----------|--------|--------|--------------|--------------|--------------|--------------|-------------------|
| 1 | 101 | 3 | 3 | 31500 | 31500 | true | true | true |

### RP-002 Reconciliation Details

| Run | Batch ID | Staged | Posted | Staged Total | Posted Total | Counts Match | Totals Match | Expectation Match |
|-----|----------|--------|--------|--------------|--------------|--------------|--------------|-------------------|
| 1 | 102 | 3 | 3 | 31500 | 31500 | true | true | true |
| 2 | 103 | 3 | 3 | 31500 | 31500 | true | true | true |

### RP-003 Reconciliation Details

| Run | Batch ID | Staged | Posted | Staged Total | Posted Total | Counts Match | Totals Match | Expectation Match |
|-----|----------|--------|--------|--------------|--------------|--------------|--------------|-------------------|
| 1 | 104 | 2 | 2 | 6500 | 6500 | true | true | true |

## Violations / Mismatches

No unexpected contract violations or replay mismatches were observed.

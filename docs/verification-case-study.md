# Verification Case Study

## The problem

When migrating a COBOL batch pipeline to Java, unit tests are not enough. Unit tests verify the new code against its own specification. They do not verify that the new code matches what the legacy system actually did.

Two systems can have identical unit test coverage and still diverge in production — on monetary precision, on case sensitivity, on implicit normalization behavior that was never documented. The legacy system's behavior is the ground truth, and it exists only as running code (or, in many cases, as decommissioned code no longer available to run). The question "how do you know the new system is correct?" cannot be answered by pointing at test counts.

The Verification Lab is the answer. It runs the modernized pipeline against fixed golden datasets, compares the actual database output against legacy-style baselines, and produces durable, reviewable evidence. A reviewer can open a report after a PR and see exactly which fields diverged, by how much, and whether any of those divergences have been investigated and accepted.

---

## Design decisions

**Baselines are checked-in documents, not live legacy snapshots.**
There is no running legacy system. The baselines in `verification-lab/expected-output/` are human-authored JSON files representing what the legacy system would have produced, constructed from knowledge of legacy behavior. This is honest about the constraint: the alternative — generating baselines from an automated legacy run — would require a working legacy environment, which does not exist in this migration scenario. Human-authored baselines make the provenance explicit and auditable.

**Comparison is field-level, not hash-based.**
A hash comparison tells you whether outputs differ but not where or why. Field-level comparison produces discrepancy items with specific dot-paths (`transactions[2].amountCents`, `totalPostedCents`) that a reviewer can trace to a cause, investigate, and act on. The cost is more implementation; the payoff is actionable findings instead of binary pass/fail with no diagnostic value.

**The approval workflow is part of the design, not an escape hatch.**
Modernization changes that alter behavior are not bugs by definition. A stricter validation rule, a corrected rounding algorithm, a more precise error message — these are intentional improvements. The approval mechanism exists to document the human judgment that converts a finding from Fail to Approved Difference. An approval entry is a durable artifact: it records who reviewed it, when, and why, scoped to the specific field and value. It is not a suppression rule.

**Normalization is universal, not per-dataset.**
The same four normalization rules apply to every dataset: trim whitespace, collapse interior spaces, sort transactions by `(accountId, amountCents)`, sort errors alphabetically. There is no per-dataset normalization configuration. DS-001 proves the rules work without masking real differences; DS-002 and DS-003 confirm they do not hide monetary or disposition divergence. Keeping normalization universal prevents the rules themselves from becoming a source of hidden false negatives.

---

## The three datasets as a progression

The datasets are not independent test cases. They build on each other to establish trust in the tool before using it to surface real findings.

### DS-001 — Happy Path with Normalization Noise

Before a FAIL result can be trusted, the tool must demonstrate it does not produce false positives on cosmetic differences. A comparison engine that flags whitespace variations is useless — reviewers will ignore its findings.

DS-001 is three clean records with one formatting artifact: the legacy baseline contains `"description": "Batch  posted"` — a double space. The modern pipeline writes `"Batch posted"`. After interior whitespace is collapsed, the values match. The lab produces a clean PASS with zero discrepancies.

This matters because it establishes the baseline case for the tool itself. An employer or reviewer can ask "what would happen if the outputs differed only in whitespace?" and point to DS-001 as the documented answer: the lab normalizes it and passes.

### DS-002 — Monetary Precision / Accumulating Drift

DS-002 tests the lab's ability to catch the subtlest class of monetary error: drift that is invisible at the record-count level.

The structural shape of the output looks healthy. Four records staged, four posted, reconciliation balanced by count — identical to DS-001. The divergence is in the amounts: legacy COBOL truncated intermediate decimal calculations; Java rounds correctly. Three of four records carry a delta of +1 or +2 cents. The aggregate total drifts by +4 cents.

The lab fails. It reports four discrepancies: one per affected record (`transactions[0].amountCents`, `transactions[1].amountCents`, `transactions[3].amountCents`) and one for the aggregate (`totalPostedCents`).

This matters because the classic first-pass check — "are the counts right?" — would pass this dataset. So would a hash-based comparison if someone chose a hash of counts and keys only. The per-field, per-record comparison is what catches the drift. And because no approval entry exists, CI fails: monetary divergence is not quietly tolerated.

### DS-003 — Behavioral Divergence / Disposition Change

DS-003 is the hardest class of finding. The systems do not disagree on formatting or on arithmetic. They disagree on whether a record is valid.

One input record has `direction = "d"` (lowercase). Legacy applied implicit case normalization and posted it. Modern enforces strict `D` or `C` and rejects it. Same input record — different business outcome. The baseline expects four posted transactions and a total of 18,000 cents. Actual output: three posted, one rejected, 16,000 cents total.

The lab fails, and surfaces the exact findings: `postedCount` 4 vs. 3, `rejectedCount` 0 vs. 1, `errorCount` 0 vs. 1, `totalPostedCents` 18,000 vs. 16,000.

A reviewer must answer four explicit questions before this can be approved: Was lowercase direction ever valid operationally? Did the upstream feed historically send lowercase values? Was legacy case normalization intentional behavior or accidental leniency? Would rejecting lowercase now break upstream compatibility or improve correctness? The approval entry encodes the investigation result as a durable artifact — not a verbal agreement, not a Jira comment, but a checked-in, reviewable, auditable record of the decision.

This is what makes DS-003 the most instructive scenario: it demonstrates that the lab is not just catching data errors. It is surfacing decisions that were implicitly made by the legacy system and forcing them to be made explicitly by humans during the modernization.

---

## What the lab does not do

The three datasets demonstrate the verification methodology. They do not exhaustively test the pipeline, and they are not intended to. Baselines are human-authored; a sufficiently wrong baseline would produce false Passes. The lab surfaces findings and encodes review criteria — it does not replace the judgment of a reviewer who understands the business rules and the upstream contract. These are known limitations, not design failures.

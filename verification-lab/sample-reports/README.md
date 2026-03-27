# Sample Evidence Reports

Representative output from a local verification lab run against DS-002 (Monetary Precision / Accumulating Drift).

| File | Format | What it shows |
|------|--------|---------------|
| `verification-ds-002-sample.html` | HTML | Reviewer-facing report: FAIL badge, 4 discrepancies (3 per-record amount deltas + 1 aggregate drift), no approved differences |
| `verification-ds-002-sample.json` | JSON | Machine-readable equivalent, suitable for CI artifact storage and downstream tooling |

DS-002 was chosen because it is the most instructive: record counts and business keys match, but per-record monetary values diverge by 1–2 cents (Java rounding vs. legacy COBOL truncation), accumulating to 4 cents of aggregate drift. The lab fails on all four fields with no approval entry present.

In CI, reports are generated fresh on every run and uploaded as a GitHub Actions artifact (`verification-lab-reports-{sha}`, retained 90 days).

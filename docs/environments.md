# Environment Roles

This document defines the environments used in this project, how the
Spring profiles map to databases, and how changes move from `dev` to
`test` to `main`.

## Environment matrix

| Branch | Spring profile | Database | Purpose | Disposable |
|---|---|---|---|---|
| `dev` | `dev` | `modernize_dev` | Day-to-day development and iteration | No |
| `test` | `test` | `modernize_test` | Promotion verification before `main` | No |
| `main` | `prod` | `modernize_prod` | Production-style, release-ready state | No |
| local sandbox | `sandbox` | `modernize_buildtest` | Rebuild-from-scratch proof and isolated trial runs | Yes |

The default Spring profile is `sandbox`, configured in
`app/src/main/resources/application.yml`.

## Databases

### modernize_buildtest

Disposable sandbox database.
Used to prove that the SQL scripts in `sql/` can rebuild the schema
and seed data from scratch, to run the batch pipeline against a
known clean state, and as the target database for integration tests.

This database uses `012b_seed_small_data.sql` (small, test-scoped
accounts and merchants) instead of the full `012_seed_data.sql`.
This gives integration tests a predictable, minimal set of rows:
accounts 2001–2003 (active), 2004 (frozen), and merchants 3001–3002.

This database can be dropped and rebuilt at any time.

### modernize_dev

Primary development database.
Used while working on the `dev` branch and testing changes during normal
development.

This database is not intended to be dropped casually.

### modernize_test

Promotion verification database.
Used after merging `dev` into `test` to confirm the promoted code and
scripts behave correctly in a controlled integration environment.

This database should stay stable enough to support repeatable promotion
checks.

### modernize_prod

Production-style database.
Used to simulate the protected state represented by the `main` branch.

This database is intentionally the most protected environment in the repo.
If it must be reset, do so deliberately and only with explicit intent.

## Spring profiles

Profile-specific configuration files are in `app/src/main/resources/`.

| Profile | Database | Config file | Typical use |
|---|---|---|---|
| `sandbox` (default) | `modernize_buildtest` | `application-sandbox.yml` | Local rebuild and isolated pipeline runs |
| `dev` | `modernize_dev` | `application-dev.yml` | Active development |
| `test` | `modernize_test` | `application-test.yml` | Verification after promotion to `test` |
| `prod` | `modernize_prod` | `application-prod.yml` | Production-style release simulation |
| `sched` | (inherits from primary) | `application-sched.yml` | Overrides schedule to every minute for local scheduler testing; compose with `dev,sched` |
| `batchtest` | `modernize_buildtest` | `application-batchtest.yml` (test/resources) | Integration tests only — loaded by `@ActiveProfiles("batchtest")` |

The `batchtest` profile is defined in `app/src/test/resources/` and is
only visible during `mvn test`. It connects to `modernize_buildtest`
with the web server disabled (`web-application-type: none`) and
`spring.batch.job.enabled=false` so no job runs automatically on
context startup.

Run the application against a specific profile with (from the repo root):

    ./app/mvnw -f app/pom.xml spring-boot:run "-Dspring-boot.run.profiles=sandbox"
    ./app/mvnw -f app/pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"
    ./app/mvnw -f app/pom.xml spring-boot:run "-Dspring-boot.run.profiles=test"
    ./app/mvnw -f app/pom.xml spring-boot:run "-Dspring-boot.run.profiles=prod"

The repo includes a Maven wrapper (`app/mvnw`) — no separate Maven installation required.

## Scripts

The newer environment scripts are profile-agnostic and take a target
database when needed.

| Script | What it does |
|---|---|
| `scripts/setup-environments.ps1` | Creates and builds all four databases |
| `scripts/reset-env.ps1 -Database <name>` | Drops, recreates, rebuilds, and verifies one database |
| `scripts/verify-env.ps1 -Database <name>` | Runs verification queries against one database |
| `scripts/reset-buildtest.ps1` | Convenience wrapper for sandbox rebuild flow |
| `scripts/verify-buildtest.ps1` | Convenience wrapper for sandbox verification |

Examples:

    .\scripts\setup-environments.ps1
    .\scripts\reset-env.ps1 -Database modernize_buildtest
    .\scripts\verify-env.ps1 -Database modernize_test

## Promotion flow

This repo uses a simple three-branch promotion model:

1. Work is done on `dev`.
2. When ready, merge `dev` into `test`.
3. Run the pipeline and verification steps against the `test` profile and
   `modernize_test`.
4. If verification passes, merge `test` into `main`.
5. Tag the resulting `main` commit.

In practice, that means:

- `dev` is where changes are made first
- `test` is the verification checkpoint
- `main` is the protected, validated state

Documentation updates should follow the same route as code changes:
update on `dev`, review on `test`, then promote to `main`.

## SQL build order

These files are applied in sequence by the environment build scripts:

    001_create_schema.sql
    002_create_customers.sql
    003_create_accounts.sql
    004_create_merchants.sql
    005_create_transactions.sql
    006_create_indexes.sql
    007_create_batch_jobs.sql
    008_create_transaction_batches.sql
    009_create_batch_job_errors.sql
    010_create_staged_transactions.sql
    011_create_batch_reconciliations.sql
    012_seed_data.sql          ← variant applied here (see table below)
    013_drop_staged_account_fk.sql
    014_add_batch_id_to_transactions.sql

Seed data variants:

| File | Used by |
|---|---|
| `012_seed_data.sql` | `modernize_dev`, `modernize_test` |
| `012_seed_data_prod.sql` | `modernize_prod` |
| `012b_seed_small_data.sql` | `modernize_buildtest` (integration test accounts and merchants only) |

The routing is handled by `scripts/build-schema.ps1`.

`013_drop_staged_account_fk.sql` removes the foreign key constraint on
`staged_transactions.account_id`. This is intentional: staging is the raw intake
layer, so records with unknown account IDs must be able to load successfully and
be caught later by Rule 3 of the validate job. Without this, those failure cases
would be impossible to load and test.

`014_add_batch_id_to_transactions.sql` adds a `batch_id` column to the
`transactions` table so posted records can be traced back to the originating
batch. Applied after seeding so it does not interfere with seed data constraints.

## Docker Compose

All four databases are hosted by the PostgreSQL container defined in
`docker-compose.yml`.
Start it from the repo root with:

    docker compose up -d

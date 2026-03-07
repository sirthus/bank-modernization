# Bank Modernization

Learning project for:

- PostgreSQL
- Docker / Docker Compose
- GitHub workflow
- command-line batch processing
- later: Java / Spring Boot / Spring Batch

## Current capability

This project currently provides:

- a PostgreSQL 18 database running in Docker Compose
- version-controlled SQL scripts for rebuilding the `bank` schema
- a disposable sandbox database for rebuild validation
- a small seed dataset
- PowerShell scripts to reset, rebuild, and verify the sandbox database

## Databases

- `modernize`  
  Main working database

- `modernize_buildtest`  
  Disposable sandbox database used to prove that schema and seed scripts work from scratch

## Project structure

app/           future Java / Spring application  
docs/          notes and design documents  
sample-data/   sample inbound files (later)  
scripts/       PowerShell helper scripts  
sql/           schema and seed SQL files  

## Start PostgreSQL

From the repo root:



`docker compose up -d`  
`docker compose ps`



This drops and recreates `modernize_buildtest`, rebuilds the schema, loads seed data, and verifies the result.

`.\scripts\reset-buildtest.ps1`

## SQL build order

- `001_create_schema.sql`
- `002_create_customers.sql`
- `003_create_accounts.sql`
- `004_create_merchants.sql`
- `005_create_transactions.sql`
- `006_primary_keys.sql`
- `007_foreign_keys.sql`
- `008_foreign_key_indexes.sql`
- `009_seed_small_data.sql`

## Verification

The current verification flow checks:

- tables exist in schema `bank`
- row counts match expected seed data
- join results prove the seeded relationships work correctly

## Next steps

- light environment formalization
- batch control model
- staged transaction loading

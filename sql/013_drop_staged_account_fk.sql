-- 013_drop_staged_account_fk.sql
-- Remove the FK constraint on staged_transactions.account_id.
--
-- Staging is the raw intake layer. Records with unknown account IDs should
-- load successfully and be caught by the validate job (Rule 3: account not
-- found). Enforcing referential integrity at the staging level makes it
-- impossible to load — and therefore test — those failure cases.
--
-- Run this against all four databases to bring existing schemas in line
-- with the updated 010_create_staged_transactions.sql:
--
--   psql -U postgres -d modernize_buildtest -f sql/013_drop_staged_account_fk.sql
--   psql -U postgres -d modernize_dev       -f sql/013_drop_staged_account_fk.sql
--   psql -U postgres -d modernize_test      -f sql/013_drop_staged_account_fk.sql
--   psql -U postgres -d modernize_prod      -f sql/013_drop_staged_account_fk.sql

ALTER TABLE bank.staged_transactions
    DROP CONSTRAINT IF EXISTS staged_transactions_account_id_fkey;

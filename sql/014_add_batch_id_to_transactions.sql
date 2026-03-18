-- 014_add_batch_id_to_transactions.sql
-- Adds batch_id to bank.transactions so reconciliation can scope
-- exactly to the records posted for each batch, rather than matching
-- on field values across the entire cumulative transaction history.

ALTER TABLE bank.transactions
    ADD COLUMN batch_id INTEGER REFERENCES bank.transaction_batches(id);

-- 006_create_indexes.sql

CREATE INDEX accounts_customer_id_idx
    ON bank.accounts (customer_id);

CREATE INDEX transactions_account_id_idx
    ON bank.transactions (account_id);

CREATE INDEX transactions_merchant_id_idx
    ON bank.transactions (merchant_id);

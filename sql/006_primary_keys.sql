-- 006_primary_keys.sql

ALTER TABLE ONLY bank.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (customer_id);
ALTER TABLE ONLY bank.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (account_id);
ALTER TABLE ONLY bank.merchants
    ADD CONSTRAINT merchants_pkey PRIMARY KEY (merchant_id);

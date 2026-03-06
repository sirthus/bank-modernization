ALTER TABLE ONLY bank.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (customer_id);

ALTER TABLE ONLY bank.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (account_id);

ALTER TABLE ONLY bank.merchants
    ADD CONSTRAINT merchants_pkey PRIMARY KEY (merchant_id);

ALTER TABLE ONLY bank.transactions
    ADD CONSTRAINT transactions_pkey PRIMARY KEY (txn_id);

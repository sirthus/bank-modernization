ALTER TABLE ONLY bank.accounts
    ADD CONSTRAINT accounts_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES bank.customers(customer_id);

ALTER TABLE ONLY bank.transactions
    ADD CONSTRAINT transactions_account_id_fkey FOREIGN KEY (account_id) REFERENCES bank.accounts(account_id);

ALTER TABLE ONLY bank.transactions
    ADD CONSTRAINT transactions_merchant_id_fkey FOREIGN KEY (merchant_id) REFERENCES bank.merchants(merchant_id);
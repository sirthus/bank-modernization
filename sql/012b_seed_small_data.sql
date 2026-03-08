-- 012_seed_small_data.sql
-- Small controlled seed set for rebuild validation.

INSERT INTO bank.customers (customer_id, full_name, email, phone, created_at)
VALUES
    (1001, 'Alice Carter', 'alice.carter@example.com', '555-0101', now()),
    (1002, 'Brian Nguyen', 'brian.nguyen@example.com', '555-0102', now());

INSERT INTO bank.accounts (account_id, customer_id, account_type, status, opened_at, credit_limit_cents)
VALUES
    (2001, 1001, 'checking', 'active', CURRENT_DATE, 0),
    (2002, 1001, 'credit',   'active', CURRENT_DATE, 500000),
    (2003, 1002, 'savings',  'active', CURRENT_DATE, 0);

INSERT INTO bank.merchants (merchant_id, name, category, created_at)
VALUES
    (3001, 'H-E-B', 'groceries', now()),
    (3002, 'Shell', 'fuel', now());

INSERT INTO bank.transactions (account_id, merchant_id, direction, amount_cents, status, created_at, description)
VALUES
    (2001, 3001, 'D', 5423,  'posted',  now(), 'Groceries'),
    (2001, 3002, 'D', 3200,  'posted',  now(), 'Fuel'),
    (2002, 3001, 'D', 1899,  'posted',  now(), 'Snacks'),
    (2003, NULL, 'C', 25000, 'pending', now(), 'Deposit');

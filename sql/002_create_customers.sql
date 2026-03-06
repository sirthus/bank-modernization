CREATE TABLE bank.customers (
    customer_id bigint NOT NULL,
    full_name text NOT NULL,
    email text NOT NULL,
    phone text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);
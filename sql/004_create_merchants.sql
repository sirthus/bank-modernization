CREATE TABLE bank.merchants (
    merchant_id bigint NOT NULL,
    name text NOT NULL,
    category text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);
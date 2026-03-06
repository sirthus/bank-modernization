CREATE TABLE bank.transactions (
    txn_id bigint NOT NULL,
    account_id bigint NOT NULL,
    merchant_id bigint,
    direction character(1) NOT NULL,
    amount_cents bigint NOT NULL,
    status text DEFAULT 'posted'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    description text,
    CONSTRAINT transactions_amount_cents_check CHECK ((amount_cents > 0)),
    CONSTRAINT transactions_direction_check CHECK ((direction = ANY (ARRAY['D'::bpchar, 'C'::bpchar]))),
    CONSTRAINT transactions_status_check CHECK ((status = ANY (ARRAY['posted'::text, 'pending'::text, 'reversed'::text])))
);
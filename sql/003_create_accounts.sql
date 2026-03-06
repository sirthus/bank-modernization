CREATE TABLE bank.accounts (
    account_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    account_type text NOT NULL,
    status text DEFAULT 'active'::text NOT NULL,
    opened_at date DEFAULT CURRENT_DATE NOT NULL,
    credit_limit_cents bigint DEFAULT 0 NOT NULL,
    CONSTRAINT accounts_account_type_check CHECK ((account_type = ANY (ARRAY['checking'::text, 'savings'::text, 'credit'::text]))),
    CONSTRAINT accounts_credit_limit_cents_check CHECK ((credit_limit_cents >= 0)),
    CONSTRAINT accounts_status_check CHECK ((status = ANY (ARRAY['active'::text, 'frozen'::text, 'closed'::text])))
);
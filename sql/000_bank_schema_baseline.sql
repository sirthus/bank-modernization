--
-- PostgreSQL database dump
--

\restrict bq2cpm9HcctTJi9fJHeLuELN6UL6Bt0nshMkLuLDCL45i56X3Sa6B7y5BGrjXUo

-- Dumped from database version 18.3 (Debian 18.3-1.pgdg13+1)
-- Dumped by pg_dump version 18.3 (Debian 18.3-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: bank; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA bank;


ALTER SCHEMA bank OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: accounts; Type: TABLE; Schema: bank; Owner: postgres
--

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


ALTER TABLE bank.accounts OWNER TO postgres;

--
-- Name: accounts_account_id_seq; Type: SEQUENCE; Schema: bank; Owner: postgres
--

CREATE SEQUENCE bank.accounts_account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE bank.accounts_account_id_seq OWNER TO postgres;

--
-- Name: accounts_account_id_seq; Type: SEQUENCE OWNED BY; Schema: bank; Owner: postgres
--

ALTER SEQUENCE bank.accounts_account_id_seq OWNED BY bank.accounts.account_id;


--
-- Name: customers; Type: TABLE; Schema: bank; Owner: postgres
--

CREATE TABLE bank.customers (
    customer_id bigint NOT NULL,
    full_name text NOT NULL,
    email text NOT NULL,
    phone text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE bank.customers OWNER TO postgres;

--
-- Name: customers_customer_id_seq; Type: SEQUENCE; Schema: bank; Owner: postgres
--

CREATE SEQUENCE bank.customers_customer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE bank.customers_customer_id_seq OWNER TO postgres;

--
-- Name: customers_customer_id_seq; Type: SEQUENCE OWNED BY; Schema: bank; Owner: postgres
--

ALTER SEQUENCE bank.customers_customer_id_seq OWNED BY bank.customers.customer_id;


--
-- Name: merchants; Type: TABLE; Schema: bank; Owner: postgres
--

CREATE TABLE bank.merchants (
    merchant_id bigint NOT NULL,
    name text NOT NULL,
    category text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE bank.merchants OWNER TO postgres;

--
-- Name: merchants_merchant_id_seq; Type: SEQUENCE; Schema: bank; Owner: postgres
--

CREATE SEQUENCE bank.merchants_merchant_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE bank.merchants_merchant_id_seq OWNER TO postgres;

--
-- Name: merchants_merchant_id_seq; Type: SEQUENCE OWNED BY; Schema: bank; Owner: postgres
--

ALTER SEQUENCE bank.merchants_merchant_id_seq OWNED BY bank.merchants.merchant_id;


--
-- Name: transactions; Type: TABLE; Schema: bank; Owner: postgres
--

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


ALTER TABLE bank.transactions OWNER TO postgres;

--
-- Name: transactions_txn_id_seq; Type: SEQUENCE; Schema: bank; Owner: postgres
--

CREATE SEQUENCE bank.transactions_txn_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE bank.transactions_txn_id_seq OWNER TO postgres;

--
-- Name: transactions_txn_id_seq; Type: SEQUENCE OWNED BY; Schema: bank; Owner: postgres
--

ALTER SEQUENCE bank.transactions_txn_id_seq OWNED BY bank.transactions.txn_id;


--
-- Name: accounts account_id; Type: DEFAULT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.accounts ALTER COLUMN account_id SET DEFAULT nextval('bank.accounts_account_id_seq'::regclass);


--
-- Name: customers customer_id; Type: DEFAULT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.customers ALTER COLUMN customer_id SET DEFAULT nextval('bank.customers_customer_id_seq'::regclass);


--
-- Name: merchants merchant_id; Type: DEFAULT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.merchants ALTER COLUMN merchant_id SET DEFAULT nextval('bank.merchants_merchant_id_seq'::regclass);


--
-- Name: transactions txn_id; Type: DEFAULT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.transactions ALTER COLUMN txn_id SET DEFAULT nextval('bank.transactions_txn_id_seq'::regclass);


--
-- Name: accounts accounts_pkey; Type: CONSTRAINT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (account_id);


--
-- Name: customers customers_email_key; Type: CONSTRAINT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.customers
    ADD CONSTRAINT customers_email_key UNIQUE (email);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (customer_id);


--
-- Name: merchants merchants_pkey; Type: CONSTRAINT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.merchants
    ADD CONSTRAINT merchants_pkey PRIMARY KEY (merchant_id);


--
-- Name: transactions transactions_pkey; Type: CONSTRAINT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.transactions
    ADD CONSTRAINT transactions_pkey PRIMARY KEY (txn_id);


--
-- Name: accounts accounts_customer_id_fkey; Type: FK CONSTRAINT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.accounts
    ADD CONSTRAINT accounts_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES bank.customers(customer_id);


--
-- Name: transactions transactions_account_id_fkey; Type: FK CONSTRAINT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.transactions
    ADD CONSTRAINT transactions_account_id_fkey FOREIGN KEY (account_id) REFERENCES bank.accounts(account_id);


--
-- Name: transactions transactions_merchant_id_fkey; Type: FK CONSTRAINT; Schema: bank; Owner: postgres
--

ALTER TABLE ONLY bank.transactions
    ADD CONSTRAINT transactions_merchant_id_fkey FOREIGN KEY (merchant_id) REFERENCES bank.merchants(merchant_id);


--
-- PostgreSQL database dump complete
--

\unrestrict bq2cpm9HcctTJi9fJHeLuELN6UL6Bt0nshMkLuLDCL45i56X3Sa6B7y5BGrjXUo


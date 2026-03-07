docker exec pg18 psql -U postgres -d modernize_buildtest -c "\dt bank.*"

docker exec pg18 psql -U postgres -d modernize_buildtest -c "
select 'customers' as table_name, count(*) as row_count from bank.customers
union all
select 'accounts', count(*) from bank.accounts
union all
select 'merchants', count(*) from bank.merchants
union all
select 'transactions', count(*) from bank.transactions
union all
select 'batch_jobs', count(*) from bank.batch_jobs
union all
select 'transaction_batches', count(*) from bank.transaction_batches
union all
select 'batch_job_errors', count(*) from bank.batch_job_errors
union all
select 'staged_transactions', count(*) from bank.staged_transactions;
"

docker exec pg18 psql -U postgres -d modernize_buildtest -c "
select
    t.txn_id,
    a.account_id,
    c.full_name,
    m.name as merchant_name,
    t.direction,
    t.amount_cents,
    t.status
from bank.transactions t
join bank.accounts a on t.account_id = a.account_id
join bank.customers c on a.customer_id = c.customer_id
left join bank.merchants m on t.merchant_id = m.merchant_id
order by t.txn_id;
"
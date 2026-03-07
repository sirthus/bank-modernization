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

docker exec pg18 psql -U postgres -d modernize_buildtest -c "
select id, job_name, status, record_count, started_at, finished_at
  from bank.batch_jobs
 order by id;
"

docker exec pg18 psql -U postgres -d modernize_buildtest -c "
select id, batch_job_id, file_name, record_count, status
  from bank.transaction_batches
 order by id;
"

docker exec pg18 psql -U postgres -d modernize_buildtest -c "
select id, batch_id, account_id, merchant_id, direction, amount_cents, status
  from bank.staged_transactions
 order by id;
"

docker exec pg18 psql -U postgres -d modernize_buildtest -c "
select id, batch_job_id, error_message, record_ref
  from bank.batch_job_errors
 order by id;
"
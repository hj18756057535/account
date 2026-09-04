-- 审计请求关联与结果；历史结果保持未知，不回填虚构成功状态。
alter table account_operation_logs add column trace_id varchar(64);
alter table account_operation_logs add column outcome varchar(16) not null default 'unknown';

create index idx_account_audit_page on account_operation_logs (created_at, id);
create index idx_account_audit_trace on account_operation_logs (trace_id);

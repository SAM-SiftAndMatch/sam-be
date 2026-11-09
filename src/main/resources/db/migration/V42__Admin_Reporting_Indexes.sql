CREATE INDEX ix_admin_revenue_entries ON finance_entries(journal_id)
 WHERE account_key='live:platform-revenue';
CREATE INDEX ix_admin_revenue_period ON finance_journals(created_at DESC,id)
 WHERE sandbox=false;
CREATE INDEX ix_admin_users_created ON users(created_at DESC,id DESC);
CREATE INDEX ix_admin_users_role_created ON users(role,created_at DESC,id DESC);

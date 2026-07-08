-- Multi-tenant isolation (RLS) + the least-privilege application role.
--
-- The application connects as quotient_app, which is NOT the table owner and NOT
-- a superuser, so Row-Level Security applies to it. Before touching tenant data
-- the app runs `SET LOCAL app.tenant_id = '<uuid>'` inside the transaction; the
-- policies below then restrict every row to that tenant. A missing/blank setting
-- resolves to NULL, which matches no rows — fail closed, never leak.
--
-- Append-only: ledger_transaction and ledger_entry grant only SELECT + INSERT to
-- the app role. There is no UPDATE/DELETE privilege, so history cannot be
-- rewritten even by application code with a bug.

-- Least-privilege runtime role. Password is local-dev only.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'quotient_app') THEN
        CREATE ROLE quotient_app LOGIN PASSWORD 'quotient_app';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO quotient_app;

-- Reference/config tables: read-only for the app (seeded by admin/migrations).
GRANT SELECT ON tenant TO quotient_app;

-- Chart of accounts: the app provisions accounts lazily, so INSERT + SELECT.
GRANT SELECT, INSERT ON ledger_account TO quotient_app;

-- Append-only ledger core: SELECT + INSERT only. No UPDATE/DELETE by design.
GRANT SELECT, INSERT ON ledger_transaction TO quotient_app;
GRANT SELECT, INSERT ON ledger_entry TO quotient_app;

-- Mutable materialized/derived state.
GRANT SELECT, INSERT, UPDATE ON account_balance TO quotient_app;
GRANT SELECT, INSERT, UPDATE ON invoice TO quotient_app;
GRANT SELECT, INSERT ON invoice_line TO quotient_app;
GRANT SELECT, INSERT, UPDATE ON outbox TO quotient_app;

-- Identity columns draw from implicit sequences; allow their use.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO quotient_app;

-- ---------------------------------------------------------------------------
-- Row-Level Security on every tenant-scoped data table.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION current_tenant_id() RETURNS UUID
    LANGUAGE sql STABLE
    AS $$ SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid $$;

-- Helper to keep the policy definitions uniform.
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'ledger_account', 'ledger_transaction', 'ledger_entry',
        'account_balance', 'invoice', 'invoice_line', 'outbox'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I '
            || 'USING (tenant_id = current_tenant_id()) '
            || 'WITH CHECK (tenant_id = current_tenant_id())', t);
    END LOOP;
END
$$;

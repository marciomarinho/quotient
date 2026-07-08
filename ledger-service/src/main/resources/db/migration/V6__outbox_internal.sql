-- The outbox is internal infrastructure drained by a single relay that must see
-- every tenant's unpublished events. Row-Level Security (added generically in
-- V2) would hide rows from the un-tenant-bound relay, so disable it here — the
-- outbox is never exposed through a tenant-facing API.
ALTER TABLE outbox DISABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON outbox;

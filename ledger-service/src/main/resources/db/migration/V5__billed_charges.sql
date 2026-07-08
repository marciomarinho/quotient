-- Records each charge the ledger has posted, so invoices can be assembled from
-- structured per-meter detail (the ledger transaction alone doesn't carry it).
-- Idempotent on charge_id: replaying a charge from Kafka records it at most once,
-- matching the idempotent ledger posting.
CREATE TABLE billed_charge (
    charge_id      UUID PRIMARY KEY,
    tenant_id      UUID        NOT NULL REFERENCES tenant (id),
    meter_code     TEXT        NOT NULL,
    window_start   TIMESTAMPTZ NOT NULL,
    window_end     TIMESTAMPTZ NOT NULL,
    quantity_billed BIGINT     NOT NULL,
    amount_minor   BIGINT      NOT NULL,
    currency       TEXT        NOT NULL,
    plan_version   INTEGER     NOT NULL,
    invoice_id     UUID        REFERENCES invoice (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX billed_charge_tenant_period_idx
    ON billed_charge (tenant_id, window_start);
CREATE INDEX billed_charge_uninvoiced_idx
    ON billed_charge (tenant_id) WHERE invoice_id IS NULL;

-- The app records and later links charges to invoices; RLS scopes to the tenant.
GRANT SELECT, INSERT, UPDATE ON billed_charge TO quotient_app;
ALTER TABLE billed_charge ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON billed_charge
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

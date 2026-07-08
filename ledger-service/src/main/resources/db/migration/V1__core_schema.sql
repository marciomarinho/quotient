-- Quotient ledger — core schema.
--
-- Design notes (see docs/LEDGER_DESIGN.md):
--   * Money is stored as BIGINT minor units + a currency code. No floating point.
--   * The ledger is append-only: entries and transactions are never updated or
--     deleted (enforced by REVOKE in V2 and by the append-only design here).
--   * Every charge posts exactly one transaction with >= 2 entries whose debits
--     equal credits (enforced by a deferred constraint trigger in V3).
--   * All tenant-scoped tables carry tenant_id and are protected by RLS (V2).

-- Tenants. Minimal config needed by the ledger; the ingestion side owns API
-- keys (added in a later phase). Seeded with the 3 canonical demo tenants.
CREATE TABLE tenant (
    id          UUID PRIMARY KEY,
    name        TEXT        NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'ACTIVE',
    plan_code   TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Chart of accounts, per tenant. One account per (tenant, type, currency).
CREATE TABLE ledger_account (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenant (id),
    type        TEXT        NOT NULL,
    currency    TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ledger_account_type_ck
        CHECK (type IN ('RECEIVABLE', 'REVENUE', 'CREDITS', 'TAX')),
    CONSTRAINT ledger_account_unique UNIQUE (tenant_id, type, currency)
);

-- A transaction groups the entries posted together. The primary key is supplied
-- by the caller and is the deterministic charge id, so re-posting the same
-- charge is a no-op (INSERT ... ON CONFLICT DO NOTHING).
CREATE TABLE ledger_transaction (
    id               UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL REFERENCES tenant (id),
    description      TEXT        NOT NULL,
    source_charge_id UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Individual debit/credit lines. Append-only. amount_minor is always positive;
-- the direction carries the sign.
CREATE TABLE ledger_entry (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id UUID        NOT NULL REFERENCES ledger_transaction (id),
    tenant_id      UUID        NOT NULL REFERENCES tenant (id),
    account_id     UUID        NOT NULL REFERENCES ledger_account (id),
    direction      TEXT        NOT NULL,
    amount_minor   BIGINT      NOT NULL,
    currency       TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ledger_entry_direction_ck CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_entry_amount_ck    CHECK (amount_minor >= 0)
);

CREATE INDEX ledger_entry_transaction_idx ON ledger_entry (transaction_id);
CREATE INDEX ledger_entry_account_idx     ON ledger_entry (account_id);
CREATE INDEX ledger_transaction_tenant_idx ON ledger_transaction (tenant_id, created_at);

-- Materialized per-account balance, updated in the same transaction as the
-- entries. ledger-verify recomputes this from ledger_entry and asserts equality.
CREATE TABLE account_balance (
    account_id    UUID PRIMARY KEY REFERENCES ledger_account (id),
    tenant_id     UUID        NOT NULL REFERENCES tenant (id),
    balance_minor BIGINT      NOT NULL DEFAULT 0,
    currency      TEXT        NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX account_balance_tenant_idx ON account_balance (tenant_id);

-- Invoices generated from charges for a period. Links to the ledger transaction
-- posted atomically with the invoice.
CREATE TABLE invoice (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID        NOT NULL REFERENCES tenant (id),
    period_start          TIMESTAMPTZ NOT NULL,
    period_end            TIMESTAMPTZ NOT NULL,
    currency              TEXT        NOT NULL,
    total_minor           BIGINT      NOT NULL DEFAULT 0,
    tax_minor             BIGINT      NOT NULL DEFAULT 0,
    status                TEXT        NOT NULL DEFAULT 'GENERATED',
    ledger_transaction_id UUID        REFERENCES ledger_transaction (id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT invoice_period_ck CHECK (period_end > period_start)
);

CREATE INDEX invoice_tenant_idx ON invoice (tenant_id, period_start);

CREATE TABLE invoice_line (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id    UUID        NOT NULL REFERENCES invoice (id),
    tenant_id     UUID        NOT NULL REFERENCES tenant (id),
    meter_code    TEXT        NOT NULL,
    quantity      BIGINT      NOT NULL,
    amount_minor  BIGINT      NOT NULL,
    currency      TEXT        NOT NULL
);

CREATE INDEX invoice_line_invoice_idx ON invoice_line (invoice_id);

-- Transactional outbox for the invoice.created event. A relay publishes rows to
-- Kafka and stamps published_at; this keeps the DB write and the event atomic.
CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    tenant_id      UUID        NOT NULL REFERENCES tenant (id),
    aggregate_type TEXT        NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     TEXT        NOT NULL,
    payload        JSONB       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx ON outbox (created_at) WHERE published_at IS NULL;

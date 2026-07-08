# Ledger Design

The double-entry ledger is the integrity centerpiece of Quotient. It is the
authoritative record of revenue: every cent that a tenant is billed is posted
as a balanced accounting transaction. Everything downstream — invoices,
reports, reconciliation — is a *statement over* the ledger, never a
re-computation that could drift from it.

- **Storage:** PostgreSQL 17, schema managed by Flyway migrations.
- **Money:** `long` minor units (cents), currency **AUD**. The *only* place a
  `BigDecimal` appears is the GST percentage calculation (see below).
- **Isolation:** posting runs at `SERIALIZABLE` with retry-on-`40001`.

## 1. The double-entry model

Every charge posts one `ledger_transaction` with **two or more**
`ledger_entry` rows. Debits always equal credits *by construction* — the
transaction cannot exist otherwise.

For a single usage charge of net revenue `R` cents plus GST `T` cents
(`T = round(R × 0.10)`), the ledger posts:

| Entry | Account          | Type      | Debit | Credit |
|-------|------------------|-----------|-------|--------|
| 1     | `tenant.RECEIVABLE` | asset  | R + T | —      |
| 2     | `tenant.REVENUE`    | income | —     | R      |
| 3     | `tenant.TAX`        | liability | —  | T      |

`(R + T)` debited == `R + T` credited. GST is the only `BigDecimal` step:

```
BigDecimal gst = net.multiply(RATE)               // RATE = 0.10
                    .setScale(0, RoundingMode.HALF_EVEN);
long taxMinorUnits = gst.longValueExact();         // back to cents
```

`HALF_EVEN` (banker's rounding) keeps aggregate rounding bias near zero across
many charges. After the tax is computed the code returns immediately to `long`
minor units — no floating point, no `BigDecimal` arithmetic touches the stored
balances.

## 2. Account types

Balances are stored **debit-positive**: `balance = Σ debits − Σ credits`.

| Account      | Accounting class | Normal side | Debit-positive balance sign |
|--------------|------------------|-------------|-----------------------------|
| `RECEIVABLE` | asset            | debit       | positive as it grows        |
| `REVENUE`    | income           | credit      | negative as it grows        |
| `TAX`        | liability        | credit      | negative as it grows        |
| `CREDITS`    | liability        | credit      | negative as it grows        |

Storing a single signed convention (debit-positive) means the balanced-ness of
the whole ledger reduces to one assertion: **the sum of every entry's signed
amount across a transaction is zero.**

## 3. Invariants enforced at THREE layers

This is the heart of the design. The same rule — *transactions are balanced,
append-only, single-currency* — is defended independently at three layers, so a
bug or a bypass at any one layer cannot corrupt the ledger.

### Layer 1 — Database

- A **`DEFERRABLE INITIALLY DEFERRED` constraint trigger**,
  `assert_transaction_balanced`, fires at **COMMIT** (not per-row). It asserts,
  for every transaction touched in the tx:
  - `Σ debits == Σ credits`,
  - `count(entries) >= 2`,
  - all entries share a **single currency**.

  Deferring to commit lets the application insert the header and its entries in
  any order within one transaction; the check only has to hold when the work is
  done.

- The entries table is **append-only**. `UPDATE` and `DELETE` are `REVOKE`d
  from the application role. `quotient_app` holds only `SELECT` and `INSERT` on
  `ledger_transaction` and `ledger_entry`:

  ```sql
  REVOKE UPDATE, DELETE ON ledger_transaction, ledger_entry FROM quotient_app;
  GRANT  SELECT, INSERT ON ledger_transaction, ledger_entry TO   quotient_app;
  ```

  A correction is never a mutation — it is a new, balanced reversing
  transaction. History is immutable.

### Layer 2 — Application (domain)

The domain `PostingPlan` record is **unconstructable unless balanced**. Its
compact constructor validates and throws before an unbalanced plan can escape
into the posting path:

```java
public record PostingPlan(UUID txId, String currency, List<Line> lines) {
    public PostingPlan {
        if (lines.size() < 2)
            throw new IllegalArgumentException("need >= 2 entries");
        if (lines.stream().map(Line::currency).distinct().count() != 1)
            throw new IllegalArgumentException("single currency only");
        long net = lines.stream().mapToLong(Line::signedMinorUnits).sum();
        if (net != 0L)
            throw new IllegalArgumentException("debits must equal credits");
    }
}
```

Posting runs at **`SERIALIZABLE`** isolation with **retry-on-`40001`**
(`serialization_failure`): concurrent posters that would violate serial order
are aborted by PostgreSQL and transparently retried with backoff.

### Layer 3 — Consumer idempotency

Each `Charge` carries a **deterministic id** — a name-based (v5) UUID derived
from a stable key:

```
txId = uuidV5(NAMESPACE, "tenant|meter|window|dimensions|planVersion")
```

The same logical charge always hashes to the same UUID. Posting is:

```sql
INSERT INTO ledger_transaction (id, ...) VALUES (:txId, ...)
ON CONFLICT (id) DO NOTHING;
```

so **replaying the same Kafka message never double-posts**. At-least-once
delivery is safe: the second attempt is a no-op.

## 4. Row-Level Security

Every tenant-scoped table has an RLS policy keyed on
`current_setting('app.tenant_id')`. The application opens each unit of work
with:

```sql
SET LOCAL app.tenant_id = :tenantId;
```

`SET LOCAL` scopes the value to the current transaction, so a pooled
connection can never leak one tenant's setting into another tenant's query.
RLS is a hard backstop: even a query missing a `WHERE tenant_id = ?` predicate
returns only the current tenant's rows.

## 5. Invoice generation

```
POST /v1/tenants/{id}/invoices?period=YYYY-MM
```

In **one `SERIALIZABLE` transaction** this endpoint:

1. Selects the period's **uninvoiced** `billed_charge` rows for the tenant.
2. Aggregates them into one `invoice` header plus **per-meter `invoice_line`**
   rows, computing net, tax, and total (all `long` minor units, AUD).
3. **Links** each `billed_charge` to the invoice (marking it invoiced) so it
   can never be billed onto a second invoice.
4. Enqueues an `invoice.created` **outbox** row for the relay to publish.

Crucially, the invoice is a **statement over already-posted charges** —
revenue was posted to the ledger *per charge, in real time*, when the charge
was rated. The invoice does not post revenue; it only summarizes. Therefore
**invoiced totals always reconcile with the ledger**: an invoice's net equals
the `REVENUE` posted for exactly the charges it links, and its tax equals the
corresponding `TAX` postings.

## 6. End-to-end sequence

```mermaid
sequenceDiagram
    autonumber
    participant Agg as Meter Aggregator
    participant Rate as Rating Engine
    participant K as Kafka (billing.charges.v1)
    participant Led as Ledger Service
    participant DB as PostgreSQL
    participant OB as Outbox Relay
    participant Bus as Kafka (invoice.created)

    Agg->>Agg: window close -> final reading
    Agg->>Rate: usage.aggregates.v1
    Rate->>Rate: rate reading x planVersion -> Charge
    Rate->>Rate: compute deterministic txId (uuidV5)
    Rate->>K: publish Charge

    Led->>K: consume Charge
    Led->>Led: build balanced PostingPlan (Layer 2)
    Led->>DB: BEGIN SERIALIZABLE; SET LOCAL app.tenant_id
    Led->>DB: INSERT ledger_transaction ON CONFLICT DO NOTHING
    Led->>DB: INSERT >=2 ledger_entry (RECEIVABLE / REVENUE / TAX)
    Led->>DB: INSERT billed_charge
    DB-->>DB: assert_transaction_balanced fires at COMMIT (Layer 1)
    DB-->>Led: COMMIT ok (or 40001 -> retry)

    Note over Led,DB: replay of same Charge -> ON CONFLICT no-op (Layer 3)

    Led->>DB: POST /invoices?period -> BEGIN SERIALIZABLE
    Led->>DB: aggregate uninvoiced billed_charge -> invoice + lines
    Led->>DB: link charges; INSERT outbox(invoice.created)
    DB-->>Led: COMMIT
    OB->>DB: poll outbox
    OB->>Bus: publish invoice.created
    OB->>DB: mark outbox row published
```

## 7. `ledger-verify`

`ledger-verify` is a task (run in **CI** and available on demand) that proves
the materialized balances have not drifted from the source-of-truth entries.
For every tenant it:

1. Recomputes each account's balance **from raw `ledger_entry` rows**:
   `Σ debits − Σ credits`.
2. Asserts the recomputed value **equals** the stored `account_balance` row.
3. Fails loudly (non-zero exit) on any mismatch, naming the tenant and account.

Because entries are append-only and every transaction is balanced, a healthy
ledger satisfies two global invariants that `ledger-verify` also checks:

- Per transaction: signed entry sum == 0.
- Per tenant: `Σ` of all account balances across the double-entry set == 0.

Any drift is therefore a bug in the balance materialization, never lost
history — the raw entries can always rebuild the truth.

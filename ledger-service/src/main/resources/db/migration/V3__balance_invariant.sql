-- Layer 1 of the ledger integrity invariants (see docs/LEDGER_DESIGN.md): a
-- database-enforced guarantee that every posted transaction is balanced.
--
-- The trigger is a DEFERRABLE INITIALLY DEFERRED constraint trigger, so it runs
-- at COMMIT — after all of a transaction's entries have been inserted — rather
-- than after each individual INSERT. At that point it asserts, for the affected
-- ledger_transaction:
--   * at least two entries exist (a real double-entry posting), and
--   * total DEBIT minor units == total CREDIT minor units, and
--   * all entries share one currency.
--
-- Because it is enforced by the database, no application bug, ad-hoc SQL, or
-- Kafka replay can ever commit an unbalanced transaction.

CREATE OR REPLACE FUNCTION assert_transaction_balanced() RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
DECLARE
    debit_total   BIGINT;
    credit_total  BIGINT;
    entry_count   BIGINT;
    currency_count BIGINT;
BEGIN
    SELECT
        COALESCE(SUM(amount_minor) FILTER (WHERE direction = 'DEBIT'), 0),
        COALESCE(SUM(amount_minor) FILTER (WHERE direction = 'CREDIT'), 0),
        COUNT(*),
        COUNT(DISTINCT currency)
    INTO debit_total, credit_total, entry_count, currency_count
    FROM ledger_entry
    WHERE transaction_id = NEW.transaction_id;

    -- The transaction may have been rolled back between insert and commit; if no
    -- rows remain there is nothing to assert.
    IF entry_count = 0 THEN
        RETURN NULL;
    END IF;

    IF entry_count < 2 THEN
        RAISE EXCEPTION
            'ledger transaction % is not double-entry: only % entry(ies)',
            NEW.transaction_id, entry_count
            USING ERRCODE = 'check_violation';
    END IF;

    IF currency_count > 1 THEN
        RAISE EXCEPTION
            'ledger transaction % mixes % currencies',
            NEW.transaction_id, currency_count
            USING ERRCODE = 'check_violation';
    END IF;

    IF debit_total <> credit_total THEN
        RAISE EXCEPTION
            'ledger transaction % is unbalanced: debits=% credits=%',
            NEW.transaction_id, debit_total, credit_total
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER ledger_entry_balanced_trg
    AFTER INSERT ON ledger_entry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION assert_transaction_balanced();

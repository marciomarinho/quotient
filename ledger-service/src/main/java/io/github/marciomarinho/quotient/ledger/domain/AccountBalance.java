package io.github.marciomarinho.quotient.ledger.domain;

import io.github.marciomarinho.quotient.common.money.Money;
import java.util.UUID;

/**
 * The balance of one account.
 *
 * <p>Balances are held debit-positive: {@code balance = Σ debits − Σ credits}. So a RECEIVABLE
 * (debit-normal) account reads positive and a REVENUE/TAX (credit-normal) account reads negative.
 * The same formula is used both for the materialized {@code account_balance} rows and for the
 * verifier's recomputation from raw entries, so the two are directly comparable.
 */
public record AccountBalance(UUID accountId, AccountType type, Money balance) {}

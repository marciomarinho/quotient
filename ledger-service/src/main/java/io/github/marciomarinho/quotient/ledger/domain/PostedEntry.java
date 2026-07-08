package io.github.marciomarinho.quotient.ledger.domain;

import io.github.marciomarinho.quotient.common.money.Money;

/** One recorded ledger entry, as read back for the query API and audit. */
public record PostedEntry(AccountType accountType, EntryDirection direction, Money amount) {}

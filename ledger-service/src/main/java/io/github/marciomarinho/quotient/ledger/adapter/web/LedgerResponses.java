package io.github.marciomarinho.quotient.ledger.adapter.web;

import io.github.marciomarinho.quotient.ledger.domain.AccountBalance;
import io.github.marciomarinho.quotient.ledger.domain.PostedEntry;
import io.github.marciomarinho.quotient.ledger.domain.PostedTransaction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTOs for the ledger query API. Kept separate from the domain types so the wire shape is
 * an explicit, stable contract (flat account/direction as strings, money as minor units +
 * currency).
 */
final class LedgerResponses {

  private LedgerResponses() {}

  record BalanceResponse(String accountType, long balanceMinor, String currency) {
    static BalanceResponse from(AccountBalance b) {
      return new BalanceResponse(
          b.type().name(), b.balance().amountMinor(), b.balance().currency().name());
    }
  }

  record EntryResponse(String accountType, String direction, long amountMinor, String currency) {
    static EntryResponse from(PostedEntry e) {
      return new EntryResponse(
          e.accountType().name(),
          e.direction().name(),
          e.amount().amountMinor(),
          e.amount().currency().name());
    }
  }

  record TransactionResponse(
      UUID id, String description, Instant createdAt, List<EntryResponse> entries) {
    static TransactionResponse from(PostedTransaction t) {
      return new TransactionResponse(
          t.id(),
          t.description(),
          t.createdAt(),
          t.entries().stream().map(EntryResponse::from).toList());
    }
  }
}

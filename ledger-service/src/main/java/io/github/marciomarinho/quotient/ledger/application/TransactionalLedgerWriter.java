package io.github.marciomarinho.quotient.ledger.application;

import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.ledger.domain.PostingPlan;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the single database transaction in which a posting is written.
 *
 * <p>Runs at {@code SERIALIZABLE} isolation so concurrent postings that would interleave into an
 * inconsistent balance are refused by PostgreSQL (SQLSTATE 40001) rather than corrupting state;
 * {@link LedgerPostingService} retries those. It is a separate bean so the {@code @Transactional}
 * proxy actually applies when {@link LedgerPostingService} calls it (a self-invocation would bypass
 * the proxy).
 */
@Component
public class TransactionalLedgerWriter {

  private final LedgerRepository repository;

  public TransactionalLedgerWriter(LedgerRepository repository) {
    this.repository = repository;
  }

  @Transactional(isolation = Isolation.SERIALIZABLE)
  public PostOutcome postOnce(PostingPlan plan) {
    return repository.post(plan);
  }

  /**
   * Post a charge and record its structured detail in the same SERIALIZABLE transaction, so the
   * double-entry postings and the billed-charge record used for invoicing can never diverge. Both
   * are idempotent on the charge id.
   */
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public PostOutcome postChargeOnce(Charge charge, PostingPlan plan) {
    PostOutcome outcome = repository.post(plan);
    if (outcome == PostOutcome.POSTED) {
      repository.recordBilledCharge(charge);
    }
    return outcome;
  }
}

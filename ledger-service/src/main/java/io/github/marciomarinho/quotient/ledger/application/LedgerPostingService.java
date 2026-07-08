package io.github.marciomarinho.quotient.ledger.application;

import io.github.marciomarinho.quotient.common.event.Charge;
import io.github.marciomarinho.quotient.ledger.domain.DoubleEntryPosting;
import io.github.marciomarinho.quotient.ledger.domain.PostingPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

/**
 * Posts charges to the ledger.
 *
 * <p>This is the layer-3 guard of the integrity model: it builds a balanced {@link PostingPlan}
 * from the charge (layer 2 — an unbalanced plan cannot be constructed) and hands it to the
 * SERIALIZABLE {@link TransactionalLedgerWriter} (layer 1 — the database trigger also enforces
 * balance). Because the transaction id is the charge's deterministic id and the insert is {@code ON
 * CONFLICT DO NOTHING}, replaying the same charge from Kafka can never double-post.
 *
 * <p>SERIALIZABLE conflicts (SQLSTATE 40001, surfaced as {@link ConcurrencyFailureException}) are
 * retried a bounded number of times.
 */
@Service
public class LedgerPostingService {

  private static final Logger LOG = LoggerFactory.getLogger(LedgerPostingService.class);
  private static final int MAX_ATTEMPTS = 5;

  private final TransactionalLedgerWriter writer;
  private final DoubleEntryPosting posting;
  private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

  public LedgerPostingService(
      TransactionalLedgerWriter writer,
      DoubleEntryPosting posting,
      io.micrometer.core.instrument.MeterRegistry meterRegistry) {
    this.writer = writer;
    this.posting = posting;
    this.meterRegistry = meterRegistry;
  }

  /** Post a charge, retrying on serialization failures. Idempotent per charge id. */
  public PostOutcome post(Charge charge) {
    PostingPlan plan = posting.forCharge(charge);

    ConcurrencyFailureException last = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        PostOutcome outcome = writer.postChargeOnce(charge, plan);
        meterRegistry
            .counter(
                "quotient.ledger.postings",
                "tenant",
                charge.tenantId().asString(),
                "outcome",
                outcome.name().toLowerCase(java.util.Locale.ROOT))
            .increment();
        return outcome;
      } catch (ConcurrencyFailureException e) {
        last = e;
        LOG.warn(
            "serialization conflict posting charge {} (attempt {}/{}), retrying",
            plan.transactionId(),
            attempt,
            MAX_ATTEMPTS);
      }
    }
    throw new LedgerPostingException(
        "failed to post charge " + plan.transactionId() + " after " + MAX_ATTEMPTS + " attempts",
        last);
  }
}

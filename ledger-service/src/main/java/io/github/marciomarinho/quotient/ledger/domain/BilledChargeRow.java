package io.github.marciomarinho.quotient.ledger.domain;

import io.github.marciomarinho.quotient.common.money.Money;
import java.util.UUID;

/** A single billed charge as read back for invoice assembly. */
public record BilledChargeRow(UUID chargeId, String meterCode, long quantity, Money amount) {}

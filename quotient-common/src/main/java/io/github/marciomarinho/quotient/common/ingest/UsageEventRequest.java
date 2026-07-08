package io.github.marciomarinho.quotient.common.ingest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.Map;

/**
 * The HTTP request body for a single usage event on {@code POST /v1/usage/events}.
 *
 * <p>Part of the ingestion API contract shared by both gateway implementations (reactive and
 * virtual-threads), so it lives in {@code quotient-common}. It is distinct from the {@code
 * UsageEvent} Kafka record: the request carries only what the client supplies; the gateway resolves
 * the tenant from the API key and stamps {@code receivedAt} before publishing.
 *
 * <p>Bean Validation annotations express the static rules (present, non-blank, non-negative).
 * Temporal rules that need "now" — {@code occurredAt} not more than 5 minutes in the future — and
 * meter existence are enforced in the gateway service, not here.
 *
 * @param idempotencyKey client-supplied dedup key, unique per logical event
 * @param meterCode the meter this event counts against (e.g. {@code llm.tokens.input})
 * @param quantity the quantity in the meter's unit (>= 0)
 * @param dimensions optional dimensions (e.g. {@code model}, {@code region})
 * @param occurredAt when the usage actually happened (client clock)
 */
public record UsageEventRequest(
    @NotBlank String idempotencyKey,
    @NotBlank String meterCode,
    @PositiveOrZero(message = "quantity must be >= 0") long quantity,
    Map<String, String> dimensions,
    @NotNull Instant occurredAt) {}

package io.github.marciomarinho.quotient.common.ingest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The HTTP request body for {@code POST /v1/usage/events:batch} — up to 1,000 usage events in one
 * call. Each element is validated individually.
 */
public record UsageEventBatchRequest(
    @NotEmpty @Size(max = 1000, message = "a batch may contain at most 1000 events") @Valid
        List<UsageEventRequest> events) {}

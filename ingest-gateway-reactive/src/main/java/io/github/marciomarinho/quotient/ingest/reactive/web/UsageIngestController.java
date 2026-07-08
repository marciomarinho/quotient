package io.github.marciomarinho.quotient.ingest.reactive.web;

import io.github.marciomarinho.quotient.common.ingest.BatchIngestResponse;
import io.github.marciomarinho.quotient.common.ingest.IngestAcceptance;
import io.github.marciomarinho.quotient.common.ingest.UsageEventBatchRequest;
import io.github.marciomarinho.quotient.common.ingest.UsageEventRequest;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ingest.reactive.auth.ApiKeyAuthWebFilter;
import io.github.marciomarinho.quotient.ingest.reactive.ingest.ReactiveIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** The reactive usage ingestion API. Tenant comes from the exchange (bound by the auth filter). */
@RestController
@RequestMapping("/v1/usage")
@Tag(name = "Usage ingestion", description = "Ingest token-usage telemetry events (reactive)")
@SecurityRequirement(name = "apiKey")
public class UsageIngestController {

  private final ReactiveIngestionService ingestionService;

  public UsageIngestController(ReactiveIngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  @PostMapping("/events")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Ingest a single usage event")
  public Mono<IngestAcceptance> ingest(
      @Valid @RequestBody UsageEventRequest request, ServerWebExchange exchange) {
    return ingestionService.ingest(tenant(exchange), request);
  }

  @PostMapping("/events:batch")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Ingest up to 1000 usage events in one call")
  public Mono<BatchIngestResponse> ingestBatch(
      @Valid @RequestBody UsageEventBatchRequest request, ServerWebExchange exchange) {
    return ingestionService.ingestBatch(tenant(exchange), request.events());
  }

  private static TenantId tenant(ServerWebExchange exchange) {
    return exchange.getRequiredAttribute(ApiKeyAuthWebFilter.TENANT_ATTRIBUTE);
  }
}

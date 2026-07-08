package io.github.marciomarinho.quotient.ingest.vthreads.web;

import io.github.marciomarinho.quotient.common.ingest.BatchIngestResponse;
import io.github.marciomarinho.quotient.common.ingest.IngestAcceptance;
import io.github.marciomarinho.quotient.common.ingest.UsageEventBatchRequest;
import io.github.marciomarinho.quotient.common.ingest.UsageEventRequest;
import io.github.marciomarinho.quotient.common.tenant.TenantContext;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ingest.vthreads.ingest.UsageIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The usage ingestion API. The tenant is taken from {@link TenantContext} (bound by the API-key
 * filter), never from the request body.
 */
@RestController
@RequestMapping("/v1/usage")
@Tag(name = "Usage ingestion", description = "Ingest token-usage telemetry events")
@SecurityRequirement(name = "apiKey")
public class UsageIngestController {

  private final UsageIngestionService ingestionService;

  public UsageIngestController(UsageIngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  @PostMapping("/events")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Ingest a single usage event")
  public IngestAcceptance ingest(@Valid @RequestBody UsageEventRequest request) {
    TenantId tenant = TenantContext.require();
    return ingestionService.ingest(tenant, request);
  }

  @PostMapping("/events:batch")
  @Operation(summary = "Ingest up to 1000 usage events in one call")
  public ResponseEntity<BatchIngestResponse> ingestBatch(
      @Valid @RequestBody UsageEventBatchRequest request) {
    TenantId tenant = TenantContext.require();
    return ResponseEntity.accepted().body(ingestionService.ingestBatch(tenant, request.events()));
  }
}

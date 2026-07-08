package io.github.marciomarinho.quotient.ledger.adapter.web;

import io.github.marciomarinho.quotient.common.tenant.TenantContext;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.adapter.web.LedgerResponses.BalanceResponse;
import io.github.marciomarinho.quotient.ledger.adapter.web.LedgerResponses.TransactionResponse;
import io.github.marciomarinho.quotient.ledger.application.LedgerQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped read API for the ledger. The tenant is always taken from {@link TenantContext}
 * (bound from the verified identity), never from a path or query parameter, so one tenant can never
 * address another's data by crafting a URL.
 */
@RestController
@RequestMapping("/v1/ledger")
@PreAuthorize("hasAnyRole('tenant-viewer', 'tenant-admin', 'platform-operator')")
public class LedgerQueryController {

  private final LedgerQueryService queryService;

  public LedgerQueryController(LedgerQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping("/balances")
  public List<BalanceResponse> balances() {
    TenantId tenant = TenantContext.require();
    return queryService.balances(tenant).stream().map(BalanceResponse::from).toList();
  }

  @GetMapping("/transactions")
  public List<TransactionResponse> transactions(@RequestParam(defaultValue = "50") int limit) {
    TenantId tenant = TenantContext.require();
    return queryService.recentTransactions(tenant, limit).stream()
        .map(TransactionResponse::from)
        .toList();
  }

  @GetMapping("/transactions/{id}")
  public ResponseEntity<TransactionResponse> transaction(@PathVariable UUID id) {
    TenantId tenant = TenantContext.require();
    return queryService
        .transaction(tenant, id)
        .map(TransactionResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}

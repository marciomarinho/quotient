package io.github.marciomarinho.quotient.ledger.adapter.web;

import io.github.marciomarinho.quotient.common.tenant.TenantContext;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the current tenant into {@link TenantContext} for the duration of each request, and clears
 * it afterwards so nothing leaks across pooled/virtual threads.
 *
 * <p><strong>Phase-3 stand-in.</strong> Tenant identity here comes from the {@code X-Tenant-Id}
 * header. This is deliberately temporary: Phase 9a replaces it with the {@code tenant_id} claim of
 * a verified Keycloak JWT, feeding the exact same {@code TenantContext} → {@code SET LOCAL
 * app.tenant_id} chain. Authorization will then be derived from the token, never from a request-
 * supplied value.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

  static final String TENANT_HEADER = "X-Tenant-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(TENANT_HEADER);
    boolean bound = false;
    if (header != null && !header.isBlank()) {
      try {
        TenantContext.set(TenantId.fromString(header.trim()));
        bound = true;
      } catch (IllegalArgumentException ignored) {
        // Malformed header -> leave unbound; controllers requiring a tenant will 400.
      }
    }
    try {
      chain.doFilter(request, response);
    } finally {
      if (bound) {
        TenantContext.clear();
      }
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Actuator endpoints are not tenant-scoped.
    return request.getRequestURI().startsWith("/actuator");
  }
}

package io.github.marciomarinho.quotient.ingest.vthreads.auth;

import io.github.marciomarinho.quotient.common.tenant.TenantContext;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates ingestion requests by the {@code Authorization: Bearer <api key>} header, resolves
 * the tenant, and binds it into {@link TenantContext} for the request. A missing or invalid key is
 * rejected with 401 before any handler runs; the binding is always cleared afterwards so nothing
 * leaks across the pooled/virtual threads that serve requests.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final ApiKeyStore apiKeyStore;

  public ApiKeyAuthFilter(ApiKeyStore apiKeyStore) {
    this.apiKeyStore = apiKeyStore;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<TenantId> tenant = extractKey(request).flatMap(apiKeyStore::resolve);
    if (tenant.isEmpty()) {
      writeUnauthorized(response);
      return;
    }
    TenantContext.set(tenant.get());
    org.slf4j.MDC.put("tenant.id", tenant.get().asString());
    try {
      chain.doFilter(request, response);
    } finally {
      org.slf4j.MDC.remove("tenant.id");
      TenantContext.clear();
    }
  }

  private static Optional<String> extractKey(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      return Optional.empty();
    }
    String key = header.substring(BEARER_PREFIX.length()).trim();
    return key.isEmpty() ? Optional.empty() : Optional.of(key);
  }

  private static void writeUnauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getWriter()
        .write("{\"error\":\"unauthorized\",\"message\":\"invalid or missing API key\"}");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    // Health/metrics and API docs are not tenant-authenticated.
    return path.startsWith("/actuator")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs");
  }
}

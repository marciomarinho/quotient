package io.github.marciomarinho.quotient.ledger.security;

import io.github.marciomarinho.quotient.common.tenant.TenantContext;
import io.github.marciomarinho.quotient.common.tenant.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the current tenant from the verified JWT's {@code tenant_id} claim into {@link
 * TenantContext} for the request — the single source of tenant identity on the query/admin path.
 *
 * <p>This is the critical invariant: the {@code tenant_id} claim (never a request parameter) feeds
 * the same {@code TenantContext} → Postgres {@code SET LOCAL app.tenant_id} chain as the API-key
 * path, so a token for tenant A can only ever touch tenant A's rows under RLS.
 *
 * <p>Runs after the Spring Security filter chain (which authenticates the JWT), so the {@link
 * JwtAuthenticationToken} is available here.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JwtTenantContextFilter extends OncePerRequestFilter {

  static final String TENANT_CLAIM = "tenant_id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean bound = bindTenantFromJwt();
    try {
      chain.doFilter(request, response);
    } finally {
      if (bound) {
        MDC.remove("tenant.id");
        TenantContext.clear();
      }
    }
  }

  private static boolean bindTenantFromJwt() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
      return false;
    }
    Jwt jwt = jwtAuth.getToken();
    String tenantClaim = jwt.getClaimAsString(TENANT_CLAIM);
    if (tenantClaim == null || tenantClaim.isBlank()) {
      return false;
    }
    TenantId tenantId = TenantId.fromString(tenantClaim);
    TenantContext.set(tenantId);
    MDC.put("tenant.id", tenantId.asString());
    return true;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/actuator")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs");
  }
}

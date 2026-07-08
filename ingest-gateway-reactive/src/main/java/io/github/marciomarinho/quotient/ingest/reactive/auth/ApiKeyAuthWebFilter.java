package io.github.marciomarinho.quotient.ingest.reactive.auth;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Authenticates requests by API key and binds the tenant into the exchange attributes for the
 * duration of the request — the reactive equivalent of the vthreads gateway's filter.
 *
 * <p>The Argon2 verification is CPU-heavy, so it runs on a bounded-elastic scheduler rather than
 * the event loop; the rest of the chain stays non-blocking (verified by BlockHound in tests).
 */
@Component
public class ApiKeyAuthWebFilter implements WebFilter {

  /** Attribute key under which the resolved {@link TenantId} is stored. */
  public static final String TENANT_ATTRIBUTE = "quotient.tenantId";

  private static final String BEARER_PREFIX = "Bearer ";

  private final ApiKeyStore apiKeyStore;

  public ApiKeyAuthWebFilter(ApiKeyStore apiKeyStore) {
    this.apiKeyStore = apiKeyStore;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    if (isUnauthenticated(path)) {
      return chain.filter(exchange);
    }
    Optional<String> key = extractKey(exchange);
    if (key.isEmpty()) {
      return unauthorized(exchange);
    }
    return Mono.fromCallable(() -> apiKeyStore.resolve(key.get()))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            tenant ->
                tenant
                    .map(
                        id -> {
                          exchange.getAttributes().put(TENANT_ATTRIBUTE, id);
                          return chain.filter(exchange);
                        })
                    .orElseGet(() -> unauthorized(exchange)));
  }

  private static boolean isUnauthenticated(String path) {
    return path.startsWith("/actuator")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/webjars");
  }

  private static Optional<String> extractKey(ServerWebExchange exchange) {
    String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      return Optional.empty();
    }
    String key = header.substring(BEARER_PREFIX.length()).trim();
    return key.isEmpty() ? Optional.empty() : Optional.of(key);
  }

  private static Mono<Void> unauthorized(ServerWebExchange exchange) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    byte[] body =
        "{\"error\":\"unauthorized\",\"message\":\"invalid or missing API key\"}"
            .getBytes(StandardCharsets.UTF_8);
    DataBuffer buffer = response.bufferFactory().wrap(body);
    return response.writeWith(Mono.just(buffer));
  }
}

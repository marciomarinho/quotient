package io.github.marciomarinho.quotient.ledger.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 resource-server security for the ledger query/admin API.
 *
 * <p>Requests must carry a Keycloak-issued JWT (validated locally against the realm JWKS — no
 * per-request IdP call). Keycloak realm roles ({@code realm_access.roles}) become Spring {@code
 * ROLE_*} authorities, so method security ({@link PreAuthorizeAuthorizationManager}) can gate
 * endpoints on {@code tenant-viewer}, {@code tenant-admin}, {@code platform-operator}.
 *
 * <p>Health, metrics, and API docs are open; everything else is authenticated.
 */
@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter())))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }

  /** Maps Keycloak realm roles to {@code ROLE_*} authorities. */
  private static JwtAuthenticationConverter converter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(SecurityConfiguration::realmRoles);
    return converter;
  }

  @SuppressWarnings("unchecked")
  private static Collection<GrantedAuthority> realmRoles(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    if (realmAccess == null) {
      return List.of();
    }
    Object roles = realmAccess.get("roles");
    if (!(roles instanceof Collection<?> roleList)) {
      return List.of();
    }
    return roleList.stream()
        .map(Object::toString)
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
  }
}

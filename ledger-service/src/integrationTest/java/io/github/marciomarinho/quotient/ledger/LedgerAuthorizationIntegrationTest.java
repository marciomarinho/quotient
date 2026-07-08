package io.github.marciomarinho.quotient.ledger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.marciomarinho.quotient.common.tenant.TenantId;
import io.github.marciomarinho.quotient.ledger.application.LedgerPostingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The security invariant: tenant identity comes from the JWT {@code tenant_id} claim, never a
 * request parameter, and role gates hold. A token for tenant A can never touch tenant B's data even
 * by crafting the URL.
 *
 * <p>JWTs are simulated with Spring Security Test's {@code jwt()} post-processor (the real Keycloak
 * loop is exercised by {@code make demo}); this focuses on the authorization logic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LedgerAuthorizationIntegrationTest extends AbstractLedgerIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired LedgerPostingService postingService;

  private static GrantedAuthority role(String role) {
    return new SimpleGrantedAuthority("ROLE_" + role);
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor asTenant(
      TenantId tenant, String... roles) {
    List<GrantedAuthority> authorities =
        java.util.Arrays.stream(roles).map(LedgerAuthorizationIntegrationTest::role).toList();
    return jwt()
        .jwt(builder -> builder.claim("tenant_id", tenant.asString()))
        .authorities(authorities);
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    mockMvc.perform(get("/v1/ledger/balances")).andExpect(status().isUnauthorized());
  }

  @Test
  void aTokenSeesOnlyItsOwnTenantsBalances() throws Exception {
    postingService.post(charge(ACME, 1000, "llm.tokens.input.authz-acme"));
    postingService.post(charge(GLOBEX, 5000, "llm.tokens.input.authz-globex"));

    // Acme's token: balances reflect Acme's charge (1000 + 10% = 1100), not Globex's.
    mockMvc
        .perform(get("/v1/ledger/balances").with(asTenant(ACME, "tenant-viewer")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.accountType == 'RECEIVABLE')].balanceMinor")
                .value(org.hamcrest.Matchers.hasItem(1100)));
  }

  @Test
  void aTokenForTenantACannotInvoiceTenantB() throws Exception {
    // Acme admin token, but the URL names Globex -> forbidden (authz from token, not URL).
    mockMvc
        .perform(
            post("/v1/tenants/{id}/invoices", GLOBEX.asString())
                .param("period", "2026-07")
                .with(asTenant(ACME, "tenant-admin")))
        .andExpect(status().isForbidden());
  }

  @Test
  void aTokenCanInvoiceItsOwnTenant() throws Exception {
    mockMvc
        .perform(
            post("/v1/tenants/{id}/invoices", ACME.asString())
                .param("period", "2026-07")
                .with(asTenant(ACME, "tenant-admin")))
        .andExpect(status().isCreated());
  }

  @Test
  void verifyIsRestrictedToPlatformOperator() throws Exception {
    mockMvc
        .perform(post("/v1/admin/ledger/verify").with(asTenant(ACME, "tenant-viewer")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(post("/v1/admin/ledger/verify").with(asTenant(ACME, "platform-operator")))
        .andExpect(status().isOk());
  }
}

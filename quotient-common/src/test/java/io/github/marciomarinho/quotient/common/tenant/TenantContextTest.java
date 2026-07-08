package io.github.marciomarinho.quotient.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

  private static final TenantId TENANT =
      TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void current_isEmptyWhenNothingBound() {
    assertThat(TenantContext.current()).isEmpty();
  }

  @Test
  void set_thenCurrentReturnsBoundTenant() {
    TenantContext.set(TENANT);

    assertThat(TenantContext.current()).contains(TENANT);
  }

  @Test
  void require_throwsWhenNoTenantBound() {
    assertThatThrownBy(TenantContext::require)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenant context is mandatory");
  }

  @Test
  void clear_removesBinding() {
    TenantContext.set(TENANT);
    TenantContext.clear();

    assertThat(TenantContext.current()).isEmpty();
  }

  @Test
  void runWith_bindsForActionAndClearsAfterwards() {
    TenantContext.runWith(TENANT, () -> assertThat(TenantContext.require()).isEqualTo(TENANT));

    assertThat(TenantContext.current()).as("binding is cleared after the action").isEmpty();
  }

  @Test
  void runWith_clearsEvenWhenActionThrows() {
    assertThatThrownBy(
            () ->
                TenantContext.runWith(
                    TENANT,
                    () -> {
                      throw new RuntimeException("boom");
                    }))
        .isInstanceOf(RuntimeException.class);

    assertThat(TenantContext.current()).as("binding must not leak on exception").isEmpty();
  }
}

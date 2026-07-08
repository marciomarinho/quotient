package io.github.marciomarinho.quotient.ledger.adapter.web;

import io.github.marciomarinho.quotient.ledger.application.LedgerVerifier;
import io.github.marciomarinho.quotient.ledger.application.VerificationReport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cross-tenant admin operations. In Phase 9a these are restricted to the {@code platform-operator}
 * role; for now they are open (Phase 3 has no auth).
 */
@RestController
@RequestMapping("/v1/admin/ledger")
public class LedgerAdminController {

  private final LedgerVerifier verifier;

  public LedgerAdminController(LedgerVerifier verifier) {
    this.verifier = verifier;
  }

  /**
   * Recompute all balances from entries and report whether they match. 200 if clean, 409 if
   * drifted.
   */
  @PostMapping("/verify")
  public ResponseEntity<VerificationReport> verify() {
    VerificationReport report = verifier.verifyAll();
    HttpStatus status = report.allMatch() ? HttpStatus.OK : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(report);
  }
}

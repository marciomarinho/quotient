package io.github.marciomarinho.quotient.ingest.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * Guards the reactive contract: no blocking calls on the event loop.
 *
 * <p>BlockHound instruments the JVM so a blocking call on a non-blocking scheduler fails fast.
 * BlockHound 1.0.11 cannot instrument JDK 25 yet (its bytecode redefinition of bootstrap classes is
 * rejected), so when instrumentation is unavailable the BlockHound-specific assertion is skipped
 * rather than failing the build; the non-blocking-chain assertion still runs. Re-enable fully once
 * a JDK-25-capable BlockHound is released.
 */
class BlockHoundTest {

  private static boolean blockHoundInstalled;

  @BeforeAll
  static void installBlockHound() {
    try {
      BlockHound.install();
      blockHoundInstalled = true;
    } catch (Throwable e) {
      blockHoundInstalled = false;
    }
  }

  @Test
  void blockHoundFlagsBlockingOnANonBlockingThread() {
    assumeTrue(blockHoundInstalled, "BlockHound could not instrument this JVM (JDK 25)");

    Mono<String> blocking =
        Mono.fromCallable(
                () -> {
                  Thread.sleep(10); // a blocking call — must be caught on a non-blocking thread
                  return "done";
                })
            .subscribeOn(Schedulers.parallel());

    assertThatThrownBy(() -> blocking.block(Duration.ofSeconds(5)))
        .satisfies(
            t -> assertThat(hasBlockingError(t)).as("BlockHound must flag the sleep").isTrue());
  }

  @Test
  void nonBlockingChainRunsCleanOnANonBlockingThread() {
    // The shape of the gateway's hot path: pure transforms, no blocking calls.
    Mono<String> chain =
        Mono.just("llm.tokens.input")
            .map(String::toUpperCase)
            .filter(s -> !s.isBlank())
            .subscribeOn(Schedulers.parallel());

    StepVerifier.create(chain).expectNext("LLM.TOKENS.INPUT").verifyComplete();
  }

  private static boolean hasBlockingError(Throwable t) {
    for (Throwable cause = t; cause != null; cause = cause.getCause()) {
      if (cause instanceof BlockingOperationError) {
        return true;
      }
    }
    return false;
  }
}

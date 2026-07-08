package io.github.marciomarinho.quotient.common;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture invariants for the domain module, enforced as tests.
 *
 * <p>Two rules the project plan calls out explicitly:
 *
 * <ol>
 *   <li><b>No floating-point money.</b> All monetary amounts are {@code long} minor units; no field
 *       in the domain may be a {@code double}/{@code float} (or their boxed forms).
 *   <li><b>Domain is framework-free.</b> {@code quotient-common} is the domain / ports layer and
 *       must not depend on Spring, Kafka, or JDBC — adapters at the edges own those. (Jackson is
 *       permitted: it is the event-schema binding, not an infrastructure framework.)
 * </ol>
 */
@AnalyzeClasses(packages = "io.github.marciomarinho.quotient.common")
class ArchitectureTest {

  @ArchTest
  static final ArchRule no_floating_point_anywhere_in_the_domain =
      noFields()
          .should()
          .haveRawType(double.class)
          .orShould()
          .haveRawType(float.class)
          .orShould()
          .haveRawType(Double.class)
          .orShould()
          .haveRawType(Float.class)
          .because("money is long minor units end-to-end; floating point is banned in the domain")
          // A prohibition rule legitimately matches nothing today — that is success, not an error.
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule domain_does_not_depend_on_frameworks =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "org.apache.kafka..",
              "java.sql..",
              "javax.sql..",
              "org.jooq..")
          .because("the domain is a framework-free ports layer; adapters live in the services")
          .allowEmptyShould(true);
}

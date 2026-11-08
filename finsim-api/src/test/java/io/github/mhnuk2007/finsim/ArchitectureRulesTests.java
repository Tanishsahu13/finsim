package io.github.mhnuk2007.finsim;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Extra rules on top of Spring Modulith's checks. */
@AnalyzeClasses(
    packages = "io.github.mhnuk2007.finsim",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTests {

  /**
   * Locked rule: no module reads another module's repositories. Keeping repositories in an {@code
   * internal} package lets Spring Modulith enforce that other modules cannot reach them.
   */
  @ArchTest
  static final ArchRule repositoriesLiveInInternalPackages =
      classes()
          .that()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .resideInAPackage("..internal..")
          .allowEmptyShould(true);
}

package com.signalops;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Enforces the module dependency rules from docs/phase-0/04-backend-architecture.md §2.
 * Boundaries stop being convention and become tests.
 */
@Tag("architecture")
class ModuleBoundaryTest {

  private static JavaClasses classes() {
    return new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.signalops");
  }

  @Test
  void sharedMustNotDependOnFeatureModules() {
    noClasses()
        .that()
        .resideInAPackage("..shared..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..auth..", "..users..", "..organizations..", "..servicecatalog..",
            "..incidents..", "..timeline..", "..notifications..", "..audit..",
            "..analytics..", "..realtime..", "..publicstatus..", "..seed..")
        .check(classes());
  }

  @Test
  void controllersMustNotTouchRepositoriesOrEntities() {
    noClasses()
        .that()
        .resideInAnyPackage("..web..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..domain..")
        .orShould()
        .dependOnClassesThat()
        .haveNameMatching(".*Repository")
        .check(classes());
  }

  @Test
  void timelineAndAuditAreEventConsumers() {
    noClasses()
        .that()
        .resideInAnyPackage("..timeline..", "..audit..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..incidents.domain..", "..organizations.domain..",
            "..servicecatalog.domain..", "..users.domain..")
        .check(classes());
  }

  @Test
  void layeredArchitectureHolds() {
    Architectures.layeredArchitecture()
        .consideringAllDependencies().withOptionalLayers(true)
        .layer("Web")
        .definedBy("..web..")
        .layer("Application")
        .definedBy("..app..")
        .layer("Domain")
        .definedBy("..domain..")
        .whereLayer("Web")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Application")
        .mayOnlyBeAccessedByLayers("Web")
        .check(classes());
  }
}

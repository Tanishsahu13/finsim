
plugins {
  alias(libs.plugins.spotless) apply false
}

// Resolve version catalog entries at the root project level.
val springBootBom = libs.spring.boot.dependencies
val springModulithBom = libs.spring.modulith.bom

// --------------------------------------------------
// Project-wide configuration
// --------------------------------------------------

allprojects {
  group = "io.github.mhnuk2007.finsim"
  version = "0.1.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }
}

// --------------------------------------------------
// Common subproject configuration
// --------------------------------------------------

subprojects {

  apply(plugin = "java-library")
  apply(plugin = "com.diffplug.spotless")

  // Java 21
  extensions.configure<JavaPluginExtension> {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(21))
    }
  }

  // Code formatting
  extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
      target("src/**/*.java")
      googleJavaFormat()
      removeUnusedImports()
    }
  }

  // JUnit 5
  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }

  // Shared dependencies
  dependencies {

    // Spring Boot BOM
    add(
      "implementation",
      platform(springBootBom)
    )

    // Spring Modulith BOM
    add(
      "implementation",
      platform(springModulithBom)
    )

    // Testing
    add("testImplementation", "org.junit.jupiter:junit-jupiter")
    add("testImplementation", "org.assertj:assertj-core")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
  }
}

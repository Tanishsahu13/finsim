plugins {
    alias(libs.plugins.spotless) apply false
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

allprojects {
    group = "io.github.mhnuk2007.finsim"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat()
            removeUnusedImports()
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        // Version alignment for every module (Spring Boot BOM also manages JUnit and AssertJ).
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:${catalog.findVersion("spring-boot").get()}"))
        "implementation"(platform("org.springframework.modulith:spring-modulith-bom:${catalog.findVersion("spring-modulith").get()}"))

        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}

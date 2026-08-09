// Formatting and static analysis only, not billionbeers.jvm.library. That convention pins Java 23
// (PROJECT_JAVA_VERSION plus the jvmTarget in billionbeers.kotlin.options), which cannot be
// produced by the JDK 17 toolchain below - a KSP processor deliberately targets the older release.
// Applying these two directly closes the actual gap: without them this module was formatted and
// linted by nothing.
plugins {
  kotlin("jvm")
  id("billionbeers.spotless")
  id("billionbeers.detekt")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17)) // Standardize on 17 for processors
  }
}

dependencies {
  api(libs.ksp.api)
  api(libs.kotlinpoet)
  api(libs.kotlinpoet.ksp)
  implementation(kotlin("stdlib"))
}

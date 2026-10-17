// Formatting and static analysis only. The jvm.library convention pins Java 23, which the
// JDK 17 toolchain below cannot produce.
plugins {
  kotlin("jvm")
  id("billionbeers.spotless")
  id("billionbeers.detekt")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

dependencies {
  api(libs.ksp.api)
  implementation(libs.bundles.kotlinPoetProcessor)
  implementation(kotlin("stdlib"))
}

// Formatting and static analysis only. See the note in snapshot-processor: the jvm.library
// convention pins Java 23, which the JDK 17 toolchain below cannot produce.
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
  api(libs.kotlinpoet)
  api(libs.kotlinpoet.ksp)
  implementation(kotlin("stdlib"))
}

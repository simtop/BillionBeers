@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins { id("billionbeers.kmp.library") }

kotlin {
  wasmJs {
    browser()
  }
}

val catalog = billionBeersCatalog()

dependencies {
  commonMainImplementation(libs.kotlinx.coroutines.core)

  commonTestImplementation(libs.coroutinesTest)
  commonTestImplementation(libs.turbine)

  jvmTestImplementation(libs.junit)
  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins { id("billionbeers.kmp.library") }

kotlin {
  wasmJs { browser() }

  android {
    namespace = "com.simtop.billionbeers.testing.domain"
  }
}

val catalog = billionBeersCatalog()

dependencies {
  commonMainImplementation(this.project(":beer-domain:api"))
  commonMainImplementation(this.project(":core-common"))
  commonMainImplementation(libs.kotlinx.coroutines.core)

  commonTestImplementation(libs.coroutinesTest)
  commonTestImplementation(libs.turbine)

  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

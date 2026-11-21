@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins { id("billionbeers.kmp.library") }

kotlin {
  wasmJs { browser() }

  android { namespace = "com.simtop.billionbeers.domain.impl" }
}

val catalog = billionBeersCatalog()

dependencies {
  commonMainImplementation(this.project(":beer-domain:api"))
  commonMainImplementation(this.project(":core-common"))
  commonMainImplementation(libs.kotlinx.coroutines.core)

  commonTestImplementation(this.project(":beer-domain:fakes"))
  commonTestImplementation(libs.coroutinesTest)

  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

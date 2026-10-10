@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins { id("billionbeers.kmp.library") }

kotlin {
  wasmJs { browser() }

  android { namespace = "com.simtop.beerdomain.impl" }
}

val catalog = billionBeersCatalog()

dependencies {
  commonMainImplementation(this.project(":beerdomain:api"))
  commonMainImplementation(this.project(":core-common"))

  commonTestImplementation(this.project(":beerdomain:fakes"))
  commonTestImplementation(libs.coroutinesTest)

  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

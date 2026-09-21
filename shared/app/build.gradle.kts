@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  id("billionbeers.kmp.compose")
}

val libs = billionBeersCatalog()

kotlin {
  wasmJs {
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":beerdomain:api"))
      implementation(project(":core-common"))
      implementation(project(":navigation-contract"))
      implementation(project(":shared:designsystem"))
      implementation(project(":shared:presentation"))
      implementation(project(":shared:favorites"))
      implementation(project(":shared:beerslist"))
      implementation(project(":shared:beersearch"))
      implementation(project(":shared:beerdetail"))
      implementation(project(":shared:beerbrowse"))
      implementation(libs.billionBeersLibrary("lifecycle-viewmodel"))
    }

    jvmTest.dependencies {
      implementation(kotlin("test"))
      implementation(project(":beerdomain:fakes"))
      implementation(project(":testing-utils"))
    }
  }
}

dependencies {
  jvmTestImplementation(libs.billionBeersLibrary("coroutinesTest"))
  jvmTestImplementation(libs.billionBeersLibrary("turbine"))
  jvmTestImplementation(libs.billionBeersLibrary("striktCore"))
  jvmTestImplementation(libs.billionBeersBundle("unitTestJunit5"))
  jvmTestRuntimeOnly(libs.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(libs.billionBeersLibrary("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

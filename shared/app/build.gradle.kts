@file:OptIn(
  org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class,
  org.jetbrains.compose.ExperimentalComposeLibrary::class,
)

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
      implementation(project(":beer-domain:api"))
      implementation(project(":beer-domain:impl"))
      implementation(project(":core-common"))
      implementation(project(":navigation-contract"))
      implementation(project(":shared:designsystem"))
      implementation(project(":shared:presentation"))
      implementation(project(":shared:favorites"))
      implementation(project(":shared:savedfilters"))
      implementation(project(":shared:beerslist"))
      implementation(project(":shared:beersearch"))
      implementation(project(":shared:beerdetail"))
      implementation(project(":shared:beerbrowse"))
      implementation(libs.billionBeersLibrary("lifecycle-viewmodel"))
      implementation(libs.billionBeersLibrary("lifecycle-runtime-compose"))
    }

    jvmTest.dependencies {
      implementation(kotlin("test"))
      implementation(project(":beer-domain:fakes"))
      implementation(project(":testing-utils"))
      implementation(compose.uiTest)
      implementation(compose.desktop.currentOs)
    }
  }
}

dependencies {
  jvmTestRuntimeOnly(libs.billionBeersLibrary("kotlinx-coroutines-swing"))
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

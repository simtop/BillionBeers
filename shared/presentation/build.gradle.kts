@file:OptIn(
  org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class,
  org.jetbrains.compose.ExperimentalComposeLibrary::class,
)

plugins {
  id("billionbeers.kmp.compose")
}

val catalog = billionBeersCatalog()

kotlin {
  wasmJs {
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core-common"))
      api(project(":beerdomain:api"))
      implementation(project(":shared:designsystem"))
      implementation(catalog.billionBeersLibrary("lifecycle-viewmodel"))
    }
    jvmTest.dependencies {
      implementation(compose.uiTest)
      implementation(compose.desktop.currentOs)
      implementation(project(":beerdomain:fakes"))
      implementation(project(":testing-utils"))
      implementation(catalog.billionBeersLibrary("turbine"))
      implementation(catalog.billionBeersLibrary("striktCore"))
      implementation(catalog.billionBeersBundle("unitTestJunit5"))
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

dependencies {
  commonTestImplementation(libs.coroutinesTest)
  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))
}

@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  id("billionbeers.kmp.compose")
}

kotlin {
  wasmJs {
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core-common"))
      implementation(project(":shared:designsystem"))
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

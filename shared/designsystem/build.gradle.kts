@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  id("billionbeers.kmp.compose")
}

kotlin {
  android {
    androidResources.enable = true
  }

  wasmJs {
    binaries.executable()
  }

  sourceSets {
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

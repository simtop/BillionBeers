@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  id("billionbeers.kmp.compose")
}

kotlin {
  wasmJs {
    binaries.executable()
  }

  sourceSets {
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

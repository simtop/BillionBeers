@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  id("billionbeers.kmp.library")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  wasmJs {
    browser()
  }

  android {
    namespace = "com.simtop.navigation.contract"
  }
}

dependencies {
  commonMainApi(libs.androidx.navigation3.runtime)
  commonMainImplementation(libs.kotlinx.serialization.json)
}

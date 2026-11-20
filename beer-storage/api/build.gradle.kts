@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  id("billionbeers.kmp.library")
}

kotlin {
  wasmJs {
    browser()
  }

  android {
    namespace = "com.simtop.billionbeers.storage.api"
  }
}

dependencies {
  commonMainImplementation(libs.kotlinx.coroutines.core)
  commonTestImplementation(libs.coroutinesTest)
  commonTestImplementation(libs.turbine)
}

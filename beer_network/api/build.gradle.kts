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
    namespace = "com.simtop.beer_network.api"
  }
}

dependencies {
  commonMainImplementation(libs.kotlinx.serialization.json)
}

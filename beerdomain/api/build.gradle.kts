@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  id("billionbeers.kmp.library")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  wasmJs {
    browser()
  }
}

kotlin {
  android {
    namespace = "com.simtop.beerdomain.api"
  }
}

dependencies {
  commonMainImplementation(this.project(":core-common"))
  commonMainImplementation(libs.kotlinx.serialization.json)
  commonMainImplementation(libs.kotlinx.coroutines.core)
}

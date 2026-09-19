@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  kotlin("multiplatform")
  alias(libs.plugins.kotlin.serialization)
  id("dev.zacsweers.metro")
}

kotlin {
  wasmJs {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":beer_storage:api"))
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
      implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
    }
  }
}

dependencies {
  commonTestImplementation(kotlin("test"))
  commonTestImplementation(libs.coroutinesTest)
  commonTestImplementation(libs.turbine)
}

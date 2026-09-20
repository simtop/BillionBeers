@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  kotlin("multiplatform")
  alias(libs.plugins.kotlin.serialization)
  id("dev.zacsweers.metro")
}

kotlin {
  wasmJs {
    browser()
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core-common"))
      implementation(project(":beerdomain:api"))
      implementation(project(":beer_network"))
      implementation(project(":beer_network:api"))
      implementation(project(":beer_data"))
      implementation(project(":beer_storage:api"))
      implementation(project(":beer_storage:browser"))
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.ktorClientCore)
      implementation(libs.ktorClientContentNegotiation)
      implementation(libs.ktorSerializationKotlinxJson)
      implementation(libs.kotlinx.serialization.json)
    }
    val wasmJsMain by getting {
      dependencies {
        implementation("io.ktor:ktor-client-js:${libs.versions.io.ktor.get()}")
      }
    }
  }
}

dependencies {
  commonTestImplementation(kotlin("test"))
  commonTestImplementation(libs.coroutinesTest)
  commonTestImplementation(libs.turbine)
}

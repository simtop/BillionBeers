@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
  alias(libs.plugins.kotlin.serialization)
  id("dev.zacsweers.metro")
}

apply(plugin = "billionbeers.spotless")

kotlin {
  wasmJs {
    browser()
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.components.resources)
      implementation(project(":core-common"))
      implementation(project(":beerdomain:api"))
      implementation(project(":beer_network"))
      implementation(project(":beer_network:api"))
      implementation(project(":beer_data"))
      implementation(project(":beer_storage:api"))
      implementation(project(":beer_storage:browser"))
      implementation(project(":shared:app"))
      implementation(project(":shared:beerbrowse"))
      implementation(project(":shared:beerdetail"))
      implementation(project(":navigation-contract"))
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

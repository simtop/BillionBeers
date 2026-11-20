@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  id("billionbeers.kmp.library")
  alias(libs.plugins.kotlin.serialization)
  id("dev.zacsweers.metro")
}

val catalog = billionBeersCatalog()

kotlin {
  android {
    namespace = "com.simtop.billionbeers.network"
  }

  wasmJs {
    browser()
  }

  sourceSets {
    val iosMain by getting {
      dependencies {
        implementation(libs.ktorClientDarwin)
      }
    }
    val wasmJsMain by getting {
      dependencies {
        implementation("io.ktor:ktor-client-js:${libs.versions.io.ktor.get()}")
      }
    }
  }
}

dependencies {
  commonMainImplementation(project(":beer-network:api"))
  commonMainImplementation(project(":core-common"))
  commonMainImplementation(libs.ktorClientCore)
  commonMainImplementation(libs.ktorClientContentNegotiation)
  commonMainImplementation(libs.ktorSerializationKotlinxJson)
  commonMainImplementation(libs.kotlinx.serialization.json)

  commonTestImplementation(libs.ktorClientMock)
  commonTestImplementation(libs.coroutinesTest)

  jvmMainImplementation(libs.ktorClientOkhttp)

  jvmTestImplementation(project(":beer-network:fixtures"))
  jvmTestImplementation(libs.junit)
  jvmTestImplementation(libs.okhttp3Mockwebserver)
  jvmTestImplementation(libs.ktorClientOkhttp)
  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))

  androidHostTestImplementation(project(":beer-network:fixtures"))
  androidHostTestImplementation(libs.okhttp3Mockwebserver)
  androidHostTestImplementation(libs.ktorClientOkhttp)
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

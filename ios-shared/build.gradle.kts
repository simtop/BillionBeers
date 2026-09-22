plugins {
  id("billionbeers.kmp.library")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
  id("dev.zacsweers.metro")
}

kotlin {
  targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
    binaries.framework {
      baseName = "BillionBeersData"
    }
  }

  sourceSets {
    val iosMain by getting {
      dependencies {
        implementation(libs.ktorClientDarwin)
        implementation(libs.sqliteBundled)
      }
    }
  }
}

dependencies {
  commonMainImplementation(project(":shared:app"))
  commonMainImplementation(project(":beer_data"))
  commonMainImplementation(project(":beer_database"))
  commonMainImplementation(project(":beer_network"))
  commonMainImplementation(project(":beerdomain:api"))
  commonMainImplementation(project(":core-common"))
  commonMainImplementation(compose.ui)
  commonMainImplementation(libs.ktorClientCore)
  commonMainImplementation(libs.ktorClientContentNegotiation)
  commonMainImplementation(libs.ktorSerializationKotlinxJson)
  commonMainImplementation(libs.kotlinx.serialization.json)
  commonMainImplementation(libs.kotlinx.coroutines.core)
  commonMainImplementation(libs.roomRuntime)

  jvmMainImplementation(libs.ktorClientOkhttp)
  jvmMainImplementation(libs.sqliteBundled)
}

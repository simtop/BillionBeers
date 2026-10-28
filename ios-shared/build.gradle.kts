plugins {
  id("billionbeers.kmp.library")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
  id("dev.zacsweers.metro")
}

// This module's expect declarations have no Android actuals, so Android metadata and host-test
// tasks fail. Its JVM tests and framework checks run in the JVM and Apple verification lanes.
extra["kmpVerificationExcludedTasks"] = setOf("allMetadataJar", "testAndroidHostTest")

kotlin {
  targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
    binaries.framework {
      baseName = "BillionBeersData"
    }
  }

  sourceSets {
    val iosMain by getting {
      dependencies {
        implementation(libs.composeMaterialIconsCore)
        implementation(libs.ktorClientDarwin)
        implementation(libs.sqliteBundled)
      }
    }
  }
}

dependencies {
  commonTestImplementation(libs.coroutinesTest)
  commonTestImplementation(project(":beerdomain:fakes"))

  commonMainImplementation(project(":shared:presentation"))
  commonMainImplementation(project(":shared:app"))
  commonMainImplementation(project(":shared:designsystem"))
  commonMainImplementation(project(":shared:beerbrowse"))
  commonMainImplementation(project(":beer_data"))
  commonMainImplementation(project(":beer_database"))
  commonMainImplementation(project(":beer_network"))
  commonMainImplementation(project(":beerdomain:api"))
  commonMainImplementation(project(":core-common"))
  commonMainImplementation(project(":navigation-contract"))
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

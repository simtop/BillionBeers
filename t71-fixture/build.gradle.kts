@file:OptIn(
  org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class,
  org.jetbrains.compose.ExperimentalComposeLibrary::class,
)

plugins {
  id("billionbeers.kmp.compose")
  id("dev.zacsweers.metro")
}

val libs = billionBeersCatalog()

kotlin {
  targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
    binaries.framework {
      baseName = "T71Fixture"
    }
  }

  wasmJs {
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.billionBeersLibrary("lifecycle-viewmodel"))
      implementation(libs.billionBeersLibrary("metrox-viewmodel"))
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.billionBeersLibrary("coroutinesTest"))
    }
    val wasmJsTest by getting {
      dependencies {
        implementation(compose.uiTest)
      }
    }
  }
}

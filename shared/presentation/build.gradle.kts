@file:OptIn(
  org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class,
  org.jetbrains.compose.ExperimentalComposeLibrary::class,
)

plugins {
  id("billionbeers.kmp.compose")
}

kotlin {
  wasmJs {
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core-common"))
      implementation(project(":beerdomain:api"))
      implementation(project(":shared:designsystem"))
    }
    jvmTest.dependencies {
      implementation(compose.uiTest)
      implementation(compose.desktop.currentOs)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

dependencies {
  commonTestImplementation(libs.coroutinesTest)
}

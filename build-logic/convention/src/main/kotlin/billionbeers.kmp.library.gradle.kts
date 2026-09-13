import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

val libs = billionBeersCatalog()

kotlin {
  sourceSets.commonMain.dependencies {
    implementation(libs.billionBeersLibrary("metro-runtime"))
  }

  jvm {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_23)
  }

  android {
    namespace = "com.simtop.billionbeers.kmp.fixture"
    compileSdk = 37
    minSdk = 28
    withHostTest {}
  }

  sourceSets {
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

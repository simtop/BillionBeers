plugins {
  id("billionbeers.kmp.feature")
}

val libs = billionBeersCatalog()

kotlin {
  sourceSets {
    commonMain.dependencies { implementation(project(":shared:presentation")) }
  }
}

dependencies { jvmTestImplementation(libs.billionBeersLibrary("striktCore")) }

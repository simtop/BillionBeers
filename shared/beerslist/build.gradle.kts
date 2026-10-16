plugins {
  id("billionbeers.kmp.feature")
}

val libs = billionBeersCatalog()

kotlin {
  sourceSets {
    commonMain.dependencies { implementation(project(":shared:presentation")) }
    jvmTest.dependencies { implementation(project(":testing-utils")) }
  }
}

dependencies { jvmTestImplementation(libs.billionBeersLibrary("striktCore")) }

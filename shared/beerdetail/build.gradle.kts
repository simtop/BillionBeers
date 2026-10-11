plugins {
  id("billionbeers.kmp.feature")
}

val libs = billionBeersCatalog()

kotlin {
  sourceSets {
    jvmTest.dependencies { implementation(project(":testing-utils")) }
  }
}

dependencies {
  jvmTestImplementation(libs.billionBeersLibrary("striktCore"))
  jvmTestImplementation(libs.billionBeersLibrary("mockk"))
  jvmTestImplementation(libs.billionBeersLibrary("kotlinx-serialization-json"))
}

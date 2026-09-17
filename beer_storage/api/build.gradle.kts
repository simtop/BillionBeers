plugins {
  id("billionbeers.kmp.library")
}

kotlin {
  android {
    namespace = "com.simtop.beer_storage.api"
  }
}

dependencies {
  commonMainImplementation(libs.kotlinx.coroutines.core)
  commonTestImplementation(libs.coroutinesTest)
  commonTestImplementation(libs.turbine)
}

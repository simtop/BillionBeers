plugins {
  id("billionbeers.kmp.library")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  android {
    namespace = "com.simtop.beer_network.api"
  }
}

dependencies {
  commonMainImplementation(libs.kotlinx.serialization.json)
}

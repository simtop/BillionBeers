plugins {
  id("billionbeers.kmp.library")
}

kotlin {
  android {
    namespace = "com.simtop.beer_network.fixtures"
  }
}

dependencies {
  commonMainApi(project(":beer_network:api"))
}

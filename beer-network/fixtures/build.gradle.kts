plugins {
  id("billionbeers.kmp.library")
}

kotlin {
  android {
    namespace = "com.simtop.billionbeers.network.fixtures"
  }
}

dependencies {
  commonMainApi(project(":beer-network:api"))
}

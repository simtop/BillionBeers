plugins {
  id("billionbeers.kmp.library")
  id("billionbeers.room")
  id("dev.zacsweers.metro")
  id("billionbeers.android.managed.device")
}

kotlin {
  android {
    namespace = "com.simtop.beer_database"
  }
}

dependencies {
  commonMainImplementation(project(":core-common"))
  commonMainImplementation(libs.kotlinx.serialization.json)

  androidMainImplementation(project(":core"))
  androidMainImplementation(libs.roomKtx)
}

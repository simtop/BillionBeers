plugins {
  id("billionbeers.kmp.library")
  id("billionbeers.room")
  id("dev.zacsweers.metro")
  id("billionbeers.android.managed.device")
}

val catalog = billionBeersCatalog()

kotlin {
  android {
    namespace = "com.simtop.beer_database"
  }
}

dependencies {
  commonMainImplementation(project(":core-common"))
  commonMainImplementation(project(":beer_storage:api"))
  commonMainImplementation(libs.kotlinx.serialization.json)
  commonMainImplementation(libs.sqliteBundled)

  androidMainImplementation(project(":core"))
  androidMainImplementation(libs.roomKtx)

  jvmTestImplementation(libs.sqliteBundled)
  jvmTestImplementation(libs.coroutinesTest)
  add("iosArm64TestImplementation", kotlin("test"))
  add("iosArm64TestImplementation", libs.coroutinesTest)
  add("iosSimulatorArm64TestImplementation", kotlin("test"))
  add("iosSimulatorArm64TestImplementation", libs.coroutinesTest)
  jvmTestImplementation(libs.junit)
  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

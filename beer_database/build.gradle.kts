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
    // Room's plugin packages migration schemas through Android test assets. KMP exposes those
    // asset sources only when Android resources are enabled.
    androidResources.enable = true
  }

  sourceSets {
    // Keep the established instrumented-test inventory in place while wiring it into the KMP
    // device-test compilation/task family.
    named("androidDeviceTest") {
      kotlin.srcDir("src/androidTest/java")
    }
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
  add("androidDeviceTestImplementation", catalog.billionBeersBundle("instrumentedTest"))
  add("androidDeviceTestImplementation", catalog.billionBeersLibrary("roomTesting"))
  jvmTestImplementation(libs.junit)
  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

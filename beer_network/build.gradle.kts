plugins {
  id("billionbeers.kmp.library")
  alias(libs.plugins.kotlin.serialization)
  id("dev.zacsweers.metro")
}

val catalog = billionBeersCatalog()

kotlin {
  android {
    namespace = "com.simtop.beer_network"
  }
}

dependencies {
  commonMainImplementation(project(":beer_network:api"))
  commonMainImplementation(project(":core-common"))
  commonMainImplementation(libs.ktorClientCore)
  commonMainImplementation(libs.ktorClientContentNegotiation)
  commonMainImplementation(libs.ktorSerializationKotlinxJson)
  commonMainImplementation(libs.kotlinx.serialization.json)

  commonTestImplementation(libs.ktorClientMock)
  commonTestImplementation(libs.coroutinesTest)

  jvmTestImplementation(project(":beer_network:fixtures"))
  jvmTestImplementation(libs.junit)
  jvmTestImplementation(libs.okhttp3Mockwebserver)
  jvmTestImplementation(libs.ktorClientOkhttp)
  jvmTestRuntimeOnly(catalog.billionBeersBundle("unitTestJunit5Runtime"))
  jvmTestRuntimeOnly(catalog.billionBeersLibrary("junit-platform-launcher"))

  androidHostTestImplementation(project(":beer_network:fixtures"))
  androidHostTestImplementation(libs.okhttp3Mockwebserver)
  androidHostTestImplementation(libs.ktorClientOkhttp)
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

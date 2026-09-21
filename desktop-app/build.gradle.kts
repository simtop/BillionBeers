plugins {
  id("billionbeers.jvm.library")
  application
  id("dev.zacsweers.metro")
}

val catalog = billionBeersCatalog()

application {
  mainClass = "com.simtop.billionbeers.desktop.MainKt"
}

dependencies {
  implementation(project(":beer_data"))
  implementation(project(":beer_database"))
  implementation(project(":beer_network"))
  implementation(project(":beerdomain:api"))
  implementation(project(":core-common"))
  implementation(project(":shared:favorites"))
  implementation(catalog.billionBeersLibrary("lifecycle-viewmodel"))
  implementation(libs.ktorClientContentNegotiation)
  implementation(libs.ktorClientOkhttp)
  implementation(libs.ktorSerializationKotlinxJson)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.roomRuntime)
  implementation(libs.sqliteBundled)

  testImplementation(libs.okhttp3Mockwebserver)
  testImplementation(project(":beer_network:fixtures"))
}

tasks.withType<JavaExec>().configureEach {
  standardInput = System.`in`
}

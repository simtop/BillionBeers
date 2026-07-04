plugins { id("org.jetbrains.kotlin.jvm") }

dependencies {
  testImplementation(libs.konsist)
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

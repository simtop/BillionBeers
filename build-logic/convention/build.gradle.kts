plugins {
    `kotlin-dsl`
}

group = "com.simtop.billionbeers.buildlogic"

dependencies {
    implementation(libs.androidToolsBuildGradle)
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.composeCompilerGradlePlugin)
    implementation(libs.roomGradlePlugin)
    implementation(libs.metro.gradle.plugin)
    implementation(libs.kspGradlePlugin)
    implementation(libs.android.junit5.plugin)
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.paparazzi.plugin)
    implementation(libs.cytoscape.webjar)
    implementation(files(files((libs as Any).javaClass.superclass.protectionDomain.codeSource.location)))

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

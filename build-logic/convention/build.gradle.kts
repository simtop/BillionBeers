plugins {
    `kotlin-dsl`
}

// TestKit needs the precompiled script plugin classes on its injected classpath. The jar is a
// stable output for those classes even when the Kotlin DSL compiler leaves the classes directory
// empty after packaging.
tasks.named<org.gradle.plugin.devel.tasks.PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    pluginClasspath.setFrom(configurations.runtimeClasspath, tasks.named("jar"))
}

group = "com.simtop.billionbeers.buildlogic"

dependencies {
    implementation(libs.androidToolsBuildGradle)
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.composeCompilerGradlePlugin)
    implementation(libs.composeMultiplatformGradlePlugin)
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
    classpath += files(tasks.named("jar"))
}

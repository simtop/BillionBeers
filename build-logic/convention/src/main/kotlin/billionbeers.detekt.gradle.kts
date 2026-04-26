import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("io.gitlab.arturbosch.detekt")
}

val libs = the<LibrariesForLibs>()

configure<DetektExtension> {
    toolVersion = libs.versions.detekt.get()
    source.setFrom(files("src/main/java", "src/main/kotlin"))
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = false
    ignoreFailures = true
}

// Detekt tasks will be configured by the plugin automatically in modern versions
// or can be customized later once we identify the correct AGP 9.0 property.
/*
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)
    }
}
*/

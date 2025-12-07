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

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
}

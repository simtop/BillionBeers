import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("io.gitlab.arturbosch.detekt")
}

val libs = the<LibrariesForLibs>()

configure<DetektExtension> {
    toolVersion = libs.versions.detekt.get()
    // Test sources are scanned too. They were excluded, so a third of the repo's Kotlin - the part
    // that decides whether the rest is correct - was invisible to static analysis even in the
    // advisory mode this used to run in. Measured cost of adding them: 13 findings across 6
    // modules, all grandfathered into the per-module baselines at adoption.
    source.setFrom(
        files(
            "src/main/java",
            "src/main/kotlin",
            "src/test/java",
            "src/test/kotlin",
            "src/androidTest/java",
            "src/androidTest/kotlin",
        )
    )
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("detekt-baseline.xml").takeIf { it.exists() }
    buildUponDefaultConfig = true
    autoCorrect = false
    // A NEW finding fails the build; the backlog present at adoption does not, because it is
    // frozen in each module's detekt-baseline.xml. This is the same shape as the Android Lint gate
    // (`abortOnError = true` over `app/lint-baseline.xml`), and AGENTS.md §5 already advertises
    // `make lint` as a rung of the verification ladder - with ignoreFailures it was output, not a
    // gate, and could not fail on anything however bad.
    //
    // Burn the baselines down over time. Never regenerate one to bury a regression: that is the
    // rule AGENTS.md states for lint-baseline.xml and it applies identically here. Regenerate only
    // when the finding is genuinely fixed, and only for the module you fixed.
    ignoreFailures = false
}

// Detekt supports max java 22 for now
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = DETEKT_JAVA_VERSION
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = DETEKT_JAVA_VERSION
    // The extension's `baseline` is null until the file exists, which is correct for the *check*
    // task - a missing baseline should mean "no exemptions", not an error. But it left the task
    // that CREATES the baseline with nowhere to write, so `detektBaseline` failed with "property
    // 'baseline' doesn't have a configured value" on precisely the modules that needed one. Give
    // the create task the path unconditionally; it is an output, not an input.
    baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))
}

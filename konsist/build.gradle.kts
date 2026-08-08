plugins { id("org.jetbrains.kotlin.jvm") }

dependencies {
  testImplementation(libs.konsist)
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly(libs.junit.platform.launcher)
}

// The rules read the rest of the repo off the filesystem - Konsist.scopeFromProject() for the
// import-based ones, and plain File reads for the ones that parse build.gradle.kts. None of that is
// visible to Gradle, so with only its own sources as inputs this task went UP-TO-DATE while the
// code it guards changed underneath it. Measured: adding a file to :feature:beerslist importing
// :feature:beerdetail - a flat FeatureModuleBoundaryTest violation - left `make konsist` reporting
// "3 up-to-date" and passing. That is the failure mode AGENTS.md §5 records twice, this time hiding
// the whole architecture gate rather than one rule, and it is worse under CI's build cache, where a
// PR touching only a feature module could restore a green result recorded before the change.
//
// Declaring the sources the rules actually read fixes it. Kotlin files feed the Konsist scope and
// .kts files feed the build-script rules; build/, bin/ and the VCS/Gradle metadata are pruned for
// the reason in AGENTS.md §2 - stale bin/ trees hold deleted sources and would fail rules on code
// that no longer exists.
val filesUnderRules =
  rootProject.layout.projectDirectory.asFileTree.matching {
    include("**/*.kt", "**/*.kts")
    exclude("**/build/**", "**/bin/**", "**/.git/**", "**/.gradle/**", "**/gradle-user-home/**")
  }

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  inputs
    .files(filesUnderRules)
    .withPropertyName("filesUnderArchitectureRules")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}

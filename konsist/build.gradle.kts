// billionbeers.jvm.library, not a bare kotlin("jvm"): this module used to opt out of the convention
// because that tier shipped JUnit 4 with no useJUnitPlatform(), and these rules are JUnit 5. Opting
// out cost it spotless and detekt too. The convention now carries the JUnit 5 tier, so the rules
// module is formatted and linted like everything else it polices.
plugins { id("billionbeers.jvm.library") }

// The JUnit 5 API, the jupiter/vintage engines and the platform launcher all come from the
// convention now, so the rules themselves are the only dependency left to declare.
dependencies { testImplementation(libs.konsist) }

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

// useJUnitPlatform() is not repeated here - billionbeers.jvm.library sets it for this tier.
tasks.withType<Test>().configureEach {
  // Gradle forks test workers with a 512 MB heap by default, and org.gradle.jvmargs does not
  // reach them - it sizes the daemon, not the worker. These rules parse every .kt file in the repo
  // into an in-memory Konsist model, and since this module joined billionbeers.jvm.library it also
  // carries jacoco instrumentation, so 512 MB is not enough: a full run died with
  // "OutOfMemoryError thrown from the UncaughtExceptionHandler in thread Test worker".
  //
  // It hid well. A single-class run (`--tests "*OneRuleTest*"`) fits in the default heap and
  // passes, and an unchanged repo leaves the task UP-TO-DATE, so the gate looked green from both
  // directions. Only a full, non-cached run reaches the ceiling - which is the run CI does.
  maxHeapSize = "2g"

  inputs
    .files(filesUnderRules)
    .withPropertyName("filesUnderArchitectureRules")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}

// Every rule file must actually have run.
//
// This gate has now failed silently three separate ways: it went UP-TO-DATE while the code it
// guards changed (fixed by declaring inputs above), it passed a single-class `--tests` run while a
// full run died of OutOfMemory (fixed by maxHeapSize above), and before either of those it simply
// never ran at all because `make test` targets testDebugUnitTest, which a pure-JVM module does not
// have (AGENTS.md §5). Each fix addressed one symptom. This addresses the shape.
//
// The check is self-maintaining on purpose: it counts `*Test.kt` rule files on disk and compares
// against the JUnit XML classes the run produced, so adding a rule raises the bar automatically and
// a hardcoded expected number can never go stale. A rule that is added but never executed - the
// exact thing AGENTS.md warns about twice - now fails the build instead of reading as coverage.
//
// Skipped when a `--tests` filter is supplied, because running one rule class is then correct.
val ruleSourceDir = layout.projectDirectory.dir("src/test/kotlin/com/simtop/konsist")
val hasTestFilter =
  gradle.startParameter.taskRequests.any { request -> request.args.any { it == "--tests" } }

tasks.named<Test>("test") {
  val resultsDir = reports.junitXml.outputLocation
  val ruleDir = ruleSourceDir
  val filtered = hasTestFilter

  doLast {
    if (filtered) return@doLast

    val expected =
      ruleDir.asFile
        .listFiles { file -> file.name.endsWith("Test.kt") }
        ?.map { it.name.removeSuffix(".kt") }
        ?.toSet()
        .orEmpty()
    val executed =
      resultsDir
        .get()
        .asFile
        .listFiles { file -> file.name.startsWith("TEST-") && file.extension == "xml" }
        ?.map { it.name.removePrefix("TEST-").removeSuffix(".xml").substringAfterLast('.') }
        ?.toSet()
        .orEmpty()

    check(expected.isNotEmpty()) {
      "Found no *Test.kt rule files in $ruleDir - the layout changed and this check, plus the " +
        "whole architecture gate, would pass vacuously."
    }

    val missing = expected - executed
    check(missing.isEmpty()) {
      "These Konsist rules exist but did not run: ${missing.sorted().joinToString(", ")}.\n" +
        "A rule that never executes looks like coverage and enforces nothing. Ran " +
        "${executed.size} of ${expected.size}."
    }
  }
}

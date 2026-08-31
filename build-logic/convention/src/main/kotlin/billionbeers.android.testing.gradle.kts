
/**
 * The JUnit 5 unit-test tier: the dependency set and the platform switch that
 * `billionbeers.android.library` and `billionbeers.android.dynamic.feature` both need, declared
 * once.
 *
 * Deliberately *not* applied by `billionbeers.android.application`. `:app`'s unit tests are JUnit 4
 * (`org.junit.Test`), and that convention applies neither `de.mannodermaus.android-junit5` nor
 * `useJUnitPlatform()`. Pulling it onto the platform here would change how its tests are discovered,
 * which is a decision to take deliberately rather than as a side effect of de-duplication. `:app`
 * shares the *bundles* instead, which is where the actual duplication was.
 *
 * `useJUnitPlatform()` moved here from the two convention plugins that each declared it.
 */
val libs = billionBeersCatalog()

dependencies {
  "testImplementation"(libs.billionBeersBundle("unitTest"))
  "testImplementation"(libs.billionBeersBundle("unitTestJunit5"))

  // The shared MainDispatcherExtension. Every ViewModel test needs Dispatchers.setMain, and
  // hand-rolling it in each module is how the JUnit 4 rule this replaced drifted out of use.
  "testImplementation"(this.project(":testing-utils"))

  "testRuntimeOnly"(libs.billionBeersBundle("unitTestJunit5Runtime"))
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

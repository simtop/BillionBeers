plugins { id("billionbeers.jvm.library") }

dependencies {
  // api, not implementation: consumers write `@RegisterExtension val mainDispatcher = ...` in their
  // own test source, so the JUnit 5 and coroutines-test types are part of this module's surface.
  api(libs.junit.jupiter.api)
  api(libs.coroutinesTest)
  implementation(this.project(":core-common"))
}

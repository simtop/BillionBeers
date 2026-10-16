plugins { id("billionbeers.jvm.library") }

dependencies {
  // api, not implementation: consumers write `@RegisterExtension val mainDispatcher = ...` in their
  // own test source, so the JUnit 5/4 and coroutines-test types are part of this module's surface.
  api(libs.junit.jupiter.api)
  api(libs.junit)
  api(libs.coroutinesTest)
  implementation(this.project(":core-common"))
}

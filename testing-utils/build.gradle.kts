plugins { id("billionbeers.jvm.library") }

dependencies {
  api(libs.junit)
  api(libs.coroutinesTest)
  implementation(project(":core-common"))
}

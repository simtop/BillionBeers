plugins { id("billionbeers.android.library") }

android { namespace = "com.simtop.beerdomain.fakes" }

dependencies {
  implementation(project(":beerdomain:api"))
  implementation(project(":core-common"))
  implementation(libs.kotlinx.coroutines.core)
}

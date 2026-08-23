plugins { id("billionbeers.android.library") }

android { namespace = "com.simtop.beerdomain.fakes" }

dependencies {
  implementation(this.project(":beerdomain:api"))
  implementation(this.project(":core-common"))
  implementation(libs.kotlinx.coroutines.core)
}

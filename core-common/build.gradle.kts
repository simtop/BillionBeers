plugins { id("billionbeers.jvm.library") }

dependencies {
  implementation(libs.coroutinesTest) // For Dispatchers? No, coroutines-core
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.androidx.annotation)
}

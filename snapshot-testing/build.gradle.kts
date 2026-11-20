plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.compose")
}

android { namespace = "com.simtop.billionbeers.snapshot.testing" }

dependencies {
  implementation(libs.androidx.compose.runtime)
  // Screenshot runs pin AsyncImage to a fixed state - see SnapshotImageEnvironment.
  implementation(libs.coil3)
}

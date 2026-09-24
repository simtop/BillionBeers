plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.compose")
}

apply(plugin = "billionbeers.android.screenshot")

android {
  namespace = "com.simtop.billionbeers.composefixture.screenshot"
}

dependencies {
  implementation(project(":compose-multiplatform-fixture")) {
    exclude(group = "org.jetbrains.compose.components", module = "components-resources")
  }
  implementation(project(":core:designsystem"))
}

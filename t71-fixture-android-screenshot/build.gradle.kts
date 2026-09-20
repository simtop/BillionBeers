plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.compose")
}

apply(plugin = "billionbeers.android.screenshot")

android {
  namespace = "com.simtop.billionbeers.t71.screenshot"
}

dependencies {
  implementation(project(":t71-fixture")) {
    exclude(group = "org.jetbrains.compose.components", module = "components-resources")
  }
  implementation(project(":core:designsystem"))
}

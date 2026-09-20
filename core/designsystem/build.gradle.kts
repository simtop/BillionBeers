plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.compose")
  id("billionbeers.android.screenshot")
  id("billionbeers.android.catalog")
}

android {
  namespace = "com.simtop.billionbeers.core.designsystem"
}

dependencies {
  api(project(":shared:designsystem"))
}

// Foundation and material3 come from billionbeers.android.compose; the shared module owns the
// portable tokens and Material3 mapping used by this Android facade.

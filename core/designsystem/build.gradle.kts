plugins {
  id("billionbeers.android.library")
  id("billionbeers.android.compose")
  id("billionbeers.android.screenshot")
  id("billionbeers.android.catalog")
}

android {
  namespace = "com.simtop.billionbeers.core.designsystem"
}

// No dependencies block: foundation and material3 are the only two this module needs, and
// billionbeers.android.compose supplies both.

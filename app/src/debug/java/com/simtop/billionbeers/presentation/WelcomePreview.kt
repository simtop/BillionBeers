package com.simtop.billionbeers.presentation

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.simtop.billionbeers.core.designsystem.theme.BillionBeersTheme

@Preview(name = "Welcome light", widthDp = 360, heightDp = 800)
@Preview(
  name = "Welcome dark",
  widthDp = 360,
  heightDp = 800,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
  name = "Welcome Spanish large",
  widthDp = 320,
  heightDp = 640,
  locale = "es",
  fontScale = 2f,
)
@Preview(name = "Welcome French", widthDp = 360, heightDp = 800, locale = "fr")
@Composable
internal fun WelcomePreview() {
  BillionBeersTheme { WelcomeScreen(onExplore = {}) }
}

@Preview(name = "Welcome landscape", widthDp = 800, heightDp = 360)
@Composable
internal fun WelcomeLandscapePreview() {
  WelcomePreview()
}

@Preview(name = "Welcome wide", widthDp = 840, heightDp = 900)
@Composable
internal fun WelcomeWidePreview() {
  WelcomePreview()
}

@Preview(name = "Welcome RTL", widthDp = 360, heightDp = 800)
@Composable
internal fun WelcomeRtlPreview() {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    WelcomePreview()
  }
}

@Preview(name = "Collection light", widthDp = 360, heightDp = 800)
@Preview(
  name = "Collection dark",
  widthDp = 360,
  heightDp = 800,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
  name = "Collection Spanish large",
  widthDp = 320,
  heightDp = 640,
  locale = "es",
  fontScale = 2f,
)
@Preview(name = "Collection French", widthDp = 360, heightDp = 800, locale = "fr")
@Composable
internal fun CollectionWelcomePreview() {
  BillionBeersTheme { WelcomeScreen(onExplore = {}, initialPage = 1) }
}

@Preview(name = "Collection wide", widthDp = 840, heightDp = 900)
@Composable
internal fun CollectionWelcomeWidePreview() {
  CollectionWelcomePreview()
}

@Preview(name = "Collection RTL", widthDp = 360, heightDp = 800)
@Composable
internal fun CollectionWelcomeRtlPreview() {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    CollectionWelcomePreview()
  }
}

package com.simtop.billionbeers.shared.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.simtop.billionbeers.shared.designsystem.controls.BrandCollectionIllustration
import com.simtop.billionbeers.shared.designsystem.controls.BrandSurface
import com.simtop.billionbeers.shared.designsystem.controls.BrandWelcomeIllustration
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme

private const val ONBOARDING_TRANSITION_MILLIS = 200

private enum class OnboardingDestination {
  RESOLVING,
  WELCOME,
  APP,
}

/** Waits for stored completion before exposing either welcome or contextual hints. */
@Composable
fun SharedOnboardingHost(
  controller: OnboardingController,
  bypassWelcome: Boolean = false,
  modifier: Modifier = Modifier,
  welcome: @Composable (() -> Unit) -> Unit,
  content: @Composable () -> Unit,
) {
  val state by controller.state.collectAsState()
  val destination =
    when {
      bypassWelcome -> OnboardingDestination.APP
      state == null -> OnboardingDestination.RESOLVING
      state?.isComplete(OnboardingIntroductions.welcome) == true -> OnboardingDestination.APP
      else -> OnboardingDestination.WELCOME
    }
  Crossfade(
    targetState = destination,
    modifier = modifier,
    animationSpec = tween(ONBOARDING_TRANSITION_MILLIS),
    label = "Onboarding destination",
  ) { current ->
    when (current) {
      OnboardingDestination.APP -> {
        CompositionLocalProvider(
          LocalOnboardingHints provides controller.takeUnless { bypassWelcome || state == null }
        ) {
          content()
        }
      }
      OnboardingDestination.RESOLVING ->
        BrandSurface(Modifier.fillMaxSize(), color = BillionBeersTheme.colorScheme.background) {}
      OnboardingDestination.WELCOME ->
        welcome { controller.complete(OnboardingIntroductions.welcome) }
    }
  }
}

@Composable
fun SharedWelcomeScreen(
  strings: OnboardingStrings,
  onComplete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val pages =
    listOf(
      SharedIntroductionPage(
        IntroductionStrings(
          "Billion Beers",
          strings.headline,
          strings.body,
          strings.next,
          strings.reassurance,
        ),
        illustration = { BrandWelcomeIllustration(it) },
      ),
      SharedIntroductionPage(
        IntroductionStrings(
          "Billion Beers",
          strings.collectionHeadline,
          strings.collectionBody,
          strings.explore,
          strings.reassurance,
        ),
        illustration = { BrandCollectionIllustration(it) },
      ),
    )
  SharedIntroductionFlow(
    pages = pages,
    pagerState = rememberPagerState(pageCount = { pages.size }),
    backLabel = strings.back,
    skipLabel = strings.skip,
    pageDescription = { strings.pageDescription(it, pages.size) },
    onComplete = onComplete,
    modifier = modifier,
  )
}

package com.simtop.billionbeers.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simtop.billionbeers.R
import com.simtop.billionbeers.shared.designsystem.controls.BrandCollectionIllustration
import com.simtop.billionbeers.shared.designsystem.controls.BrandSurface
import com.simtop.billionbeers.shared.designsystem.controls.BrandWelcomeIllustration
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import com.simtop.presentation_utils.core.LocalOnboardingHints
import com.simtop.presentation_utils.core.OnboardingIntroductions
import com.simtop.presentation_utils.custom_views.ComposeIntroductionFlow
import com.simtop.presentation_utils.custom_views.IntroductionPage

private const val ONBOARDING_TRANSITION_MILLIS = 200

private enum class OnboardingDestination {
  RESOLVING,
  WELCOME,
  APP,
}

@Composable
fun OnboardingHost(
  controller: OnboardingController,
  bypassWelcome: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val state by controller.state.collectAsStateWithLifecycle()
  val destination =
    when {
      bypassWelcome || state?.isComplete(OnboardingIntroductions.welcome) == true ->
        OnboardingDestination.APP
      state == null -> OnboardingDestination.RESOLVING
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
          LocalOnboardingHints provides controller.takeUnless { bypassWelcome }
        ) {
          content()
        }
      }
      OnboardingDestination.RESOLVING ->
        BrandSurface(Modifier.fillMaxSize(), color = BillionBeersTheme.colorScheme.background) {}
      OnboardingDestination.WELCOME ->
        WelcomeScreen(onExplore = { controller.complete(OnboardingIntroductions.welcome) })
    }
  }
}

@Composable
internal fun WelcomeScreen(
  onExplore: () -> Unit,
  modifier: Modifier = Modifier,
  initialPage: Int = 0,
) {
  val brand = stringResource(R.string.app_name)
  val footnote = stringResource(R.string.onboarding_reassurance)
  val pages =
    listOf(
      IntroductionPage(
        eyebrow = brand,
        headline = stringResource(R.string.onboarding_headline),
        body = stringResource(R.string.onboarding_body),
        action = stringResource(R.string.onboarding_next),
        footnote = footnote,
        illustration = { BrandWelcomeIllustration(it) },
      ),
      IntroductionPage(
        eyebrow = brand,
        headline = stringResource(R.string.onboarding_collection_headline),
        body = stringResource(R.string.onboarding_collection_body),
        action = stringResource(R.string.onboarding_explore),
        footnote = footnote,
        illustration = { BrandCollectionIllustration(it) },
      ),
    )
  val pageDescriptionTemplate = stringResource(R.string.onboarding_page_position)
  ComposeIntroductionFlow(
    pages = pages,
    backLabel = stringResource(R.string.onboarding_back),
    skipLabel = stringResource(R.string.onboarding_skip),
    pageDescription = { index -> String.format(pageDescriptionTemplate, index + 1, pages.size) },
    onComplete = onExplore,
    modifier = modifier,
    initialPage = initialPage,
  )
}

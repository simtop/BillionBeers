package com.simtop.billionbeers.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simtop.billionbeers.R
import com.simtop.billionbeers.shared.designsystem.controls.BrandCollectionIllustration
import com.simtop.billionbeers.shared.designsystem.controls.BrandWelcomeIllustration
import com.simtop.billionbeers.shared.presentation.OnboardingController
import com.simtop.billionbeers.shared.presentation.SharedOnboardingHost
import com.simtop.presentation_utils.custom_views.ComposeIntroductionFlow
import com.simtop.presentation_utils.custom_views.IntroductionPage

@Composable
fun OnboardingHost(
  controller: OnboardingController,
  bypassWelcome: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  SharedOnboardingHost(
    controller = controller,
    bypassWelcome = bypassWelcome,
    modifier = modifier,
    welcome = { onComplete -> WelcomeScreen(onExplore = onComplete) },
    content = content,
  )
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

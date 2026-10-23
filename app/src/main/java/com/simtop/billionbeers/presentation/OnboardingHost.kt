package com.simtop.billionbeers.presentation

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simtop.billionbeers.R
import com.simtop.billionbeers.shared.designsystem.controls.BrandSurface
import com.simtop.billionbeers.shared.designsystem.controls.BrandWelcomeIllustration
import com.simtop.billionbeers.shared.designsystem.theme.BillionBeersTheme
import com.simtop.presentation_utils.core.LocalOnboardingHints
import com.simtop.presentation_utils.core.OnboardingIntroductions
import com.simtop.presentation_utils.custom_views.ComposeIntroduction

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
  animateEntrance: Boolean = true,
) {
  val context = LocalContext.current
  val animationsEnabled =
    remember(context) {
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      ) != 0f
    }
  var entrancePlayed by rememberSaveable { mutableStateOf(false) }
  val playEntrance = remember { animateEntrance && animationsEnabled && !entrancePlayed }
  SideEffect { entrancePlayed = true }
  ComposeIntroduction(
    eyebrow = stringResource(R.string.app_name),
    headline = stringResource(R.string.onboarding_headline),
    body = stringResource(R.string.onboarding_body),
    action = stringResource(R.string.onboarding_explore),
    footnote = stringResource(R.string.onboarding_reassurance),
    onContinue = onExplore,
    illustration = { BrandWelcomeIllustration(it) },
    animateEntrance = playEntrance,
    modifier = modifier,
  )
}
